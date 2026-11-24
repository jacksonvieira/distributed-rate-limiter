package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free, in-memory token bucket rate limiter.
 *
 * <p>Each key has its own bucket. Tokens are refilled lazily: nothing runs in the background,
 * the bucket is brought up to date whenever it is used. Updates use a compare-and-set loop on an
 * immutable state object, so concurrent requests for the same key never over-grant permits.
 *
 * <p>This implementation is local to one JVM. It is the reference implementation for the
 * algorithm and the fallback used when the shared store is unavailable; the Redis-backed
 * limiter applies the same rules across instances.
 */
public final class InMemoryTokenBucketRateLimiter implements RateLimiter {

    private final TokenBucketConfig config;
    private final TimeSource timeSource;
    private final long refillPeriodNanos;
    private final ConcurrentMap<String, AtomicReference<BucketState>> buckets = new ConcurrentHashMap<>();
    private final long fullRefillNanos;
    private final IdleKeySweeper<AtomicReference<BucketState>> sweeper;

    public InMemoryTokenBucketRateLimiter(TokenBucketConfig config) {
        this(config, TimeSource.system());
    }

    public InMemoryTokenBucketRateLimiter(TokenBucketConfig config, TimeSource timeSource) {
        this.config = Objects.requireNonNull(config, "config");
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
        this.refillPeriodNanos = config.refillPeriod().toNanos();
        // After this long without use an empty bucket is full again, i.e. equal to a new one.
        this.fullRefillNanos = (long) Math.ceil((double) config.capacity() * refillPeriodNanos / config.refillTokens());
        this.sweeper = new IdleKeySweeper<>(
                buckets, bucket -> this.timeSource.nanoTime() - bucket.get().lastRefillNanos() >= fullRefillNanos);
    }

    @Override
    public RateLimitResult tryAcquire(String key, long permits) {
        validate(key, permits);
        sweeper.onCall();

        AtomicReference<BucketState> bucket = buckets.computeIfAbsent(
                key, k -> new AtomicReference<>(new BucketState(config.capacity(), timeSource.nanoTime())));

        while (true) {
            BucketState current = bucket.get();
            // Never move the bucket's clock backwards, otherwise a later call would refill twice.
            long now = Math.max(timeSource.nanoTime(), current.lastRefillNanos());
            double available = refill(current, now);

            if (available >= permits) {
                BucketState next = new BucketState(available - permits, now);
                if (bucket.compareAndSet(current, next)) {
                    return RateLimitResult.allowed(config.capacity(), (long) Math.floor(next.tokens()));
                }
            } else {
                // Persist the refill so later calls do not recompute it from an older timestamp.
                BucketState next = new BucketState(available, now);
                if (bucket.compareAndSet(current, next)) {
                    return RateLimitResult.rejected(
                            config.capacity(), (long) Math.floor(available), timeUntil(permits - available));
                }
            }
            // Another thread updated the bucket first: retry with the fresh state.
        }
    }

    /**
     * @return number of keys currently tracked (useful for metrics and tests)
     */
    public int trackedKeys() {
        return buckets.size();
    }

    private double refill(BucketState state, long now) {
        long elapsed = now - state.lastRefillNanos();
        // Multiply before dividing to keep exact results for whole refill periods.
        double gained = (double) elapsed * config.refillTokens() / refillPeriodNanos;
        return Math.min(config.capacity(), state.tokens() + gained);
    }

    private Duration timeUntil(double missingTokens) {
        long nanos = (long) Math.ceil(missingTokens * refillPeriodNanos / config.refillTokens());
        return Duration.ofNanos(Math.max(1L, nanos));
    }

    private void validate(String key, long permits) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be null or blank");
        }
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be at least 1, got " + permits);
        }
        if (permits > config.capacity()) {
            throw new IllegalArgumentException(
                    "permits (" + permits + ") exceed the bucket capacity (" + config.capacity() + ")");
        }
    }

    private record BucketState(double tokens, long lastRefillNanos) {
    }
}
