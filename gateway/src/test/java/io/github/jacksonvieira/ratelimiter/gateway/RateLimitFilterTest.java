package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class RateLimitFilterTest {

    @Test
    void retryAfterIsRoundedUpToWholeSeconds() {
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofNanos(1))).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofMillis(100))).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofSeconds(1))).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofMillis(1001))).isEqualTo(2);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofHours(1))).isEqualTo(3600);
    }

    @Test
    void callsTheBlockingLimiterOffTheCallingThread() {
        AtomicReference<Thread> limiterThread = new AtomicReference<>();
        RateLimiter limiter = (key, permits) -> {
            limiterThread.set(Thread.currentThread());
            return new RateLimitResult(true, 1, 0, Duration.ZERO);
        };
        GatewayProperties.Route route =
                new GatewayProperties.Route("/api", URI.create("http://localhost:1"), true, Duration.ofSeconds(1), Duration.ofSeconds(1));
        RateLimitFilter filter = new RateLimitFilter(route, limiter, KeyResolver.clientIp());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ping"));

        filter.filter(exchange, ignored -> Mono.empty()).block(Duration.ofSeconds(5));

        assertThat(limiterThread.get()).isNotSameAs(Thread.currentThread());
        assertThat(exchange.getResponse().getHeaders().getFirst(RateLimitFilter.LIMIT_HEADER)).isEqualTo("1");
    }
}
