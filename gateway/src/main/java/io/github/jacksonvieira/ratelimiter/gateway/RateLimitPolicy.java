package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Decides which limiter, and which client key, apply to a request.
 */
@FunctionalInterface
public interface RateLimitPolicy {

    Selection select(ServerHttpRequest request);

    /**
     * @param limiter the limiter to consult
     * @param key     the already-resolved key to consult it with
     * @param rule    name of the rule that matched, for reporting; {@link #DEFAULT_RULE} when none did
     */
    record Selection(RateLimiter limiter, String key, String rule) {}

    String DEFAULT_RULE = "default";

    /**
     * A policy that applies one limiter and one key resolver to every request.
     */
    static RateLimitPolicy single(RateLimiter limiter, KeyResolver keyResolver) {
        return request -> new Selection(limiter, keyResolver.resolve(request), DEFAULT_RULE);
    }
}
