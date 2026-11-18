package io.github.jacksonvieira.ratelimiter.core;

/**
 * Decides whether a request identified by a key (client IP, API key, user id...) may proceed.
 *
 * <p>Implementations must be thread-safe: the same instance is shared by every request.
 */
public interface RateLimiter {

    /**
     * Tries to consume a single permit for the given key.
     *
     * @param key the identity being limited, never {@code null} or blank
     * @return the decision together with the information needed for rate limit headers
     */
    default RateLimitResult tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    /**
     * Tries to consume {@code permits} permits for the given key, atomically.
     * Either all permits are granted or none is.
     *
     * @param key     the identity being limited, never {@code null} or blank
     * @param permits how many permits to consume, must be at least 1
     * @return the decision together with the information needed for rate limit headers
     * @throws IllegalArgumentException if {@code permits} is lower than 1 or greater than the limit
     */
    RateLimitResult tryAcquire(String key, long permits);
}
