package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory sliding window log: remembers the timestamp of every granted permit and allows a request
 * only if fewer than {@code limit} permits were granted in the last {@code window}.
 *
 * <p>It is exact and has no boundary burst, at the cost of memory proportional to the number of
 * permits granted within the window (up to {@code limit} per key). Each key is guarded by its own lock.
 */
public final class InMemorySlidingWindowLogRateLimiter implements RateLimiter {

    private final SlidingWindowConfig config;
    private final TimeSource timeSource;
    private final long windowNanos;
    private final ConcurrentMap<String, ArrayDeque<Long>> logs = new ConcurrentHashMap<>();

    public InMemorySlidingWindowLogRateLimiter(SlidingWindowConfig config) {
        this(config, TimeSource.system());
    }

    public InMemorySlidingWindowLogRateLimiter(SlidingWindowConfig config, TimeSource timeSource) {
        this.config = Objects.requireNonNull(config, "config");
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
        this.windowNanos = config.window().toNanos();
    }

    @Override
    public RateLimitResult tryAcquire(String key, long permits) {
        validate(key, permits);

        ArrayDeque<Long> log = logs.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (log) {
            long now = timeSource.nanoTime();
            while (!log.isEmpty() && now - log.peekFirst() >= windowNanos) {
                log.removeFirst();
            }

            long used = log.size();
            if (used + permits > config.limit()) {
                // Enough room appears once the oldest (used + permits - limit) entries leave the window.
                long toExpire = used + permits - config.limit();
                Long blocking = null;
                int index = 1;
                for (Long timestamp : log) {
                    if (index++ == toExpire) {
                        blocking = timestamp;
                        break;
                    }
                }
                Duration retryAfter = Duration.ofNanos(Math.max(1L, blocking + windowNanos - now));
                return RateLimitResult.rejected(config.limit(), config.limit() - used, retryAfter);
            }
            for (long i = 0; i < permits; i++) {
                log.addLast(now);
            }
            return RateLimitResult.allowed(config.limit(), config.limit() - log.size());
        }
    }

    private void validate(String key, long permits) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be null or blank");
        }
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be at least 1, got " + permits);
        }
        if (permits > config.limit()) {
            throw new IllegalArgumentException(
                    "permits (" + permits + ") exceed the window limit (" + config.limit() + ")");
        }
    }
}
