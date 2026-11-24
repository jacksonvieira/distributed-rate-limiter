package io.github.jacksonvieira.ratelimiter.admin;

import io.github.jacksonvieira.ratelimiter.admin.adapter.in.web.ApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Protects {@code /api/**} with a shared key when {@code admin.api-key} is set; open otherwise. */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "admin.api-key")
    ApiKeyFilter apiKeyFilter(@Value("${admin.api-key}") String key) {
        return new ApiKeyFilter(key);
    }
}
