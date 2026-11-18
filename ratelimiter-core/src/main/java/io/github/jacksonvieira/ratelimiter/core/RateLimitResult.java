package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Outcome of a rate limit check.
 *
 * <p>The fields map directly to the usual HTTP headers:
 * {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining} and {@code Retry-After}.
 *
 * @param allowed    whether the request may proceed
 * @param limit      maximum number of permits available at once
 * @param remaining  permits left after this decision
 * @param retryAfter how long the caller should wait before the request could succeed;
 *                   {@link Duration#ZERO} when the request was allowed
 */
public record RateLimitResult(boolean allowed, long limit, long remaining, Duration retryAfter) {

    public RateLimitResult {
        Objects.requireNonNull(retryAfter, "retryAfter");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must not be negative, got " + remaining);
        }
        if (retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must not be negative, got " + retryAfter);
        }
    }

    static RateLimitResult allowed(long limit, long remaining) {
        return new RateLimitResult(true, limit, remaining, Duration.ZERO);
    }

    static RateLimitResult rejected(long limit, long remaining, Duration retryAfter) {
        return new RateLimitResult(false, limit, remaining, retryAfter);
    }
}
