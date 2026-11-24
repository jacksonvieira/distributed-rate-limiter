package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jacksonvieira.ratelimiter.core.InMemoryFixedWindowRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "gateway.route.path-prefix=/api",
        "gateway.route.uri=http://localhost:1",
        "gateway.rate-limit.capacity=2",
        "gateway.rate-limit.refill-tokens=1",
        "gateway.rate-limit.refill-period=1h",
        "gateway.rate-limit.algorithm=fixed-window"
})
class AlgorithmSelectionTest {

    @Autowired
    RateLimiter rateLimiter;

    @Test
    void usesTheConfiguredAlgorithm() {
        assertThat(rateLimiter).isInstanceOf(InMemoryFixedWindowRateLimiter.class);
        assertThat(rateLimiter.tryAcquire("k").remaining()).isEqualTo(1);
    }
}
