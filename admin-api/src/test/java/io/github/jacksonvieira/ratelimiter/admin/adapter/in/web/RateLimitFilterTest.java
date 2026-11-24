package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jacksonvieira.ratelimiter.core.InMemoryTokenBucketRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.TokenBucketConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(
            new InMemoryTokenBucketRateLimiter(new TokenBucketConfig(2, 1, Duration.ofHours(1))));

    private MockHttpServletResponse call(String uri, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void rejectsACallerThatExceedsTheBurst() throws Exception {
        assertThat(call("/api/rules", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("/api/rules", "10.0.0.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = call("/api/rules", "10.0.0.1");

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotNull();
        assertThat(limited.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void limitsEachAddressSeparately() throws Exception {
        call("/api/rules", "10.0.0.1");
        call("/api/rules", "10.0.0.1");
        call("/api/rules", "10.0.0.1");

        assertThat(call("/api/rules", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitOtherPaths() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call("/actuator/health", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }
}
