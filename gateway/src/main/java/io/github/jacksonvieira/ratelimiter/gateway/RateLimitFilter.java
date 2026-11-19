package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import java.time.Duration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Applies the rate limit to requests on the route before they are forwarded.
 *
 * <p>Every limited response carries {@code X-RateLimit-Limit} and {@code X-RateLimit-Remaining};
 * rejected requests get {@code 429 Too Many Requests} with {@code Retry-After} and never reach the backend.
 */
public final class RateLimitFilter implements WebFilter, Ordered {

    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final GatewayProperties.Route route;
    private final RateLimiter rateLimiter;
    private final KeyResolver keyResolver;

    public RateLimitFilter(GatewayProperties.Route route, RateLimiter rateLimiter, KeyResolver keyResolver) {
        this.route = route;
        this.rateLimiter = rateLimiter;
        this.keyResolver = keyResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!route.matches(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        // The in-memory limiter only does CPU work, so calling it on the event loop is fine.
        // The Redis limiter (phase 2) will need a reactive variant of this call.
        RateLimitResult result = rateLimiter.tryAcquire(keyResolver.resolve(exchange.getRequest()));

        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();
        headers.set(LIMIT_HEADER, Long.toString(result.limit()));
        headers.set(REMAINING_HEADER, Long.toString(result.remaining()));

        if (result.allowed()) {
            return chain.filter(exchange);
        }
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds(result.retryAfter())));
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return response.setComplete();
    }

    /**
     * {@code Retry-After} is a whole number of seconds; round up so clients never retry too early.
     */
    static long retryAfterSeconds(Duration retryAfter) {
        long seconds = retryAfter.toSeconds();
        return retryAfter.toNanosPart() > 0 || seconds == 0 ? seconds + 1 : seconds;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
