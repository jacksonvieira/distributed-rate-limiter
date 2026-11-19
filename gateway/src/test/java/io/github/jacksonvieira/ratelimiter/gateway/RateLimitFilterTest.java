package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitFilterTest {

    @Test
    void retryAfterIsRoundedUpToWholeSeconds() {
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofNanos(1))).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofMillis(100))).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofSeconds(1))).isEqualTo(1);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofMillis(1001))).isEqualTo(2);
        assertThat(RateLimitFilter.retryAfterSeconds(Duration.ofHours(1))).isEqualTo(3600);
    }
}
