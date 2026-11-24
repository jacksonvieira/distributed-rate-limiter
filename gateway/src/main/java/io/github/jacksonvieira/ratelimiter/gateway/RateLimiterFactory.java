package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.RateLimiter;

/**
 * Builds a {@link RateLimiter} for a limit configuration, on whichever store the gateway is configured with.
 */
interface RateLimiterFactory {

    /**
     * @param limit the limit to enforce
     * @param scope name that keeps this limiter's counters apart from the others', or {@code null} for the default
     */
    RateLimiter create(GatewayProperties.RateLimit limit, String scope);
}
