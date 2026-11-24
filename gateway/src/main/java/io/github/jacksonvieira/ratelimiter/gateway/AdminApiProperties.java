package io.github.jacksonvieira.ratelimiter.gateway;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for loading rules from the admin API, bound from {@code gateway.admin-api.*}.
 *
 * @param enabled         whether to poll the admin API; when off, only {@code gateway.rules} apply
 * @param url             base URL of the admin API
 * @param refreshInterval pause between two polls
 * @param timeout         maximum time to wait for one fetch
 * @param apiKey          key sent in {@code X-API-Key}; omit when the admin API is open
 */
@ConfigurationProperties("gateway.admin-api")
public record AdminApiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8090") URI url,
        @DefaultValue("10s") Duration refreshInterval,
        @DefaultValue("3s") Duration timeout,
        String apiKey) {}
