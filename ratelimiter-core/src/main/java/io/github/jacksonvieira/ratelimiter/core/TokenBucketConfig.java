package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration of a token bucket.
 *
 * <p>The bucket holds at most {@code capacity} tokens and gains {@code refillTokens}
 * tokens every {@code refillPeriod}, continuously (not in discrete steps).
 * For example {@code new TokenBucketConfig(100, 10, Duration.ofSeconds(1))} allows bursts
 * of up to 100 requests and a sustained rate of 10 requests per second.
 *
 * @param capacity     maximum number of tokens, i.e. the largest allowed burst
 * @param refillTokens tokens added per {@code refillPeriod}
 * @param refillPeriod period over which {@code refillTokens} are added
 */
public record TokenBucketConfig(long capacity, long refillTokens, Duration refillPeriod) {

    public TokenBucketConfig {
        Objects.requireNonNull(refillPeriod, "refillPeriod");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1, got " + capacity);
        }
        if (refillTokens < 1) {
            throw new IllegalArgumentException("refillTokens must be at least 1, got " + refillTokens);
        }
        if (refillPeriod.isNegative() || refillPeriod.isZero()) {
            throw new IllegalArgumentException("refillPeriod must be positive, got " + refillPeriod);
        }
    }

    /**
     * Convenience factory for "N requests per period, with bursts up to N".
     */
    public static TokenBucketConfig perPeriod(long permits, Duration period) {
        return new TokenBucketConfig(permits, permits, period);
    }
}
