package io.github.jacksonvieira.ratelimiter.admin;

import io.github.jacksonvieira.ratelimiter.admin.adapter.in.web.ApiKeyFilter;
import io.github.jacksonvieira.ratelimiter.admin.adapter.in.web.BodySizeLimitFilter;
import io.github.jacksonvieira.ratelimiter.admin.adapter.in.web.SecurityHeadersFilter;
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
}
