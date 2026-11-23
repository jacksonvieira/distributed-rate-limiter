package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

    private static final GatewayProperties.Route ROUTE = new GatewayProperties.Route(
            "/api", URI.create("http://localhost:1"), true, Duration.ofSeconds(1), Duration.ofSeconds(1));
    private static final RateLimiter BROKEN = (key, permits) -> {
        throw new IllegalStateException("redis is down");
    };

    @Test
    void failOpenLetsTheRequestThroughWhenTheLimiterFails() {
        RateLimitFilter filter = new RateLimitFilter(
                ROUTE, BROKEN, KeyResolver.clientIp(), GatewayProperties.FailureMode.FAIL_OPEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ping"));
        AtomicInteger forwarded = new AtomicInteger();

        filter.filter(exchange, ignored -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block(Duration.ofSeconds(5));

        assertThat(forwarded).hasValue(1);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void failClosedAnswers503WhenTheLimiterFails() {
        RateLimitFilter filter = new RateLimitFilter(
                ROUTE, BROKEN, KeyResolver.clientIp(), GatewayProperties.FailureMode.FAIL_CLOSED);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ping"));
        AtomicInteger forwarded = new AtomicInteger();

        filter.filter(exchange, ignored -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block(Duration.ofSeconds(5));

        assertThat(forwarded).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void errorsFromTheBackendAreNotTreatedAsLimiterFailures() {
        RateLimiter working = (key, permits) -> new RateLimitResult(true, 1, 0, Duration.ZERO);
        RateLimitFilter filter = new RateLimitFilter(
                ROUTE, working, KeyResolver.clientIp(), GatewayProperties.FailureMode.FAIL_OPEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ping"));
        AtomicInteger forwarded = new AtomicInteger();

        assertThatThrownBy(() -> filter.filter(exchange, ignored -> {
            forwarded.incrementAndGet();
            return Mono.error(new IllegalStateException("backend exploded"));
        }).block(Duration.ofSeconds(5))).hasMessageContaining("backend exploded");

        assertThat(forwarded).hasValue(1);
    }
}
