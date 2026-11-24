package io.github.jacksonvieira.ratelimiter.admin;

import io.github.jacksonvieira.ratelimiter.admin.adapter.in.web.ApiKeyFilter;
import io.github.jacksonvieira.ratelimiter.admin.adapter.in.web.BodySizeLimitFilter;
import io.github.jacksonvieira.ratelimiter.admin.adapter.in.web.SecurityHeadersFilter;
import io.github.jacksonvieira.ratelimiter.admin.adapter.in.web.RateLimitFilter;
import io.github.jacksonvieira.ratelimiter.core.InMemoryTokenBucketRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.TokenBucketConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Protects {@code /api/**} with a shared key when {@code admin.api-key} is set (open otherwise), adds security
 * headers and caps the request body at {@code admin.max-body-bytes}.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "admin.api-key")
    ApiKeyFilter apiKeyFilter(@Value("${admin.api-key}") String key) {
        return new ApiKeyFilter(key);
    }

    @Bean
    SecurityHeadersFilter securityHeadersFilter() {
        return new SecurityHeadersFilter();
    }

    @Bean
    BodySizeLimitFilter bodySizeLimitFilter(@Value("${admin.max-body-bytes:16384}") long maxBytes) {
        return new BodySizeLimitFilter(maxBytes);
    }

    /** One token bucket per client address: bursts up to {@code admin.rate-limit.burst}, then the sustained rate. */
    @Bean
    @ConditionalOnProperty(name = "admin.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    RateLimitFilter rateLimitFilter(
            @Value("${admin.rate-limit.burst:100}") long burst,
            @Value("${admin.rate-limit.requests-per-minute:600}") long perMinute) {
        return new RateLimitFilter(
                new InMemoryTokenBucketRateLimiter(new TokenBucketConfig(burst, perMinute, Duration.ofMinutes(1))));
    }
}
