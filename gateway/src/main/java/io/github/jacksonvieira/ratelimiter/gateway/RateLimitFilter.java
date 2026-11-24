package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Applies the rate limit to requests on the route before they are forwarded.
 *
 * <p>Every limited response carries {@code X-RateLimit-Limit} and {@code X-RateLimit-Remaining};
 * rejected requests get {@code 429 Too Many Requests} with {@code Retry-After} and never reach the backend.
 */
@Slf4j
public final class RateLimitFilter implements WebFilter, Ordered {

    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final GatewayProperties.Route route;
    private final RateLimitPolicy policy;
    private final GatewayProperties.FailureMode failureMode;
    private final RateLimitObserver observer;

    public RateLimitFilter(GatewayProperties.Route route, RateLimiter rateLimiter, KeyResolver keyResolver) {
        this(route, rateLimiter, keyResolver, GatewayProperties.FailureMode.FAIL_OPEN);
    }

    public RateLimitFilter(
            GatewayProperties.Route route,
            RateLimiter rateLimiter,
            KeyResolver keyResolver,
            GatewayProperties.FailureMode failureMode) {
        this(route, RateLimitPolicy.single(rateLimiter, keyResolver), failureMode);
    }

    public RateLimitFilter(
            GatewayProperties.Route route, RateLimitPolicy policy, GatewayProperties.FailureMode failureMode) {
        this(route, policy, failureMode, RateLimitObserver.NOOP);
    }

    public RateLimitFilter(
            GatewayProperties.Route route,
            RateLimitPolicy policy,
            GatewayProperties.FailureMode failureMode,
            RateLimitObserver observer) {
        this.route = route;
        this.policy = policy;
        this.failureMode = failureMode;
        this.observer = observer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!route.matches(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        RateLimitPolicy.Selection selection = policy.select(exchange.getRequest());

        // A RateLimiter is a blocking port (the Redis adapter does network I/O), so it must never run on the
        // event loop: hand it to a pool meant for blocking calls.
        // Only the limiter call is guarded: errors raised later by the chain (the backend) must not be
        // mistaken for limiter failures, nor run the chain twice.
        return Mono.fromCallable(() -> Optional.of(selection.limiter().tryAcquire(selection.key())))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.warn("Rate limiter failed ({}), applying {}", error.toString(), failureMode);
                    observer.onDecision(selection.rule(), RateLimitObserver.Outcome.LIMITER_FAILED);
                    return Mono.just(Optional.<RateLimitResult>empty());
                })
                .flatMap(result -> result.isPresent()
                        ? respond(exchange, chain, selection.rule(), result.get())
                        : onLimiterFailure(exchange, chain));
    }

    private Mono<Void> onLimiterFailure(ServerWebExchange exchange, WebFilterChain chain) {
        if (failureMode == GatewayProperties.FailureMode.FAIL_OPEN) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> respond(
            ServerWebExchange exchange, WebFilterChain chain, String rule, RateLimitResult result) {
        observer.onDecision(
                rule, result.allowed() ? RateLimitObserver.Outcome.ALLOWED : RateLimitObserver.Outcome.REJECTED);
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
