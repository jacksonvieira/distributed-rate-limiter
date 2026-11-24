package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free, in-memory fixed window counter.
 *
 * <p>Time is cut into consecutive windows of equal length and each key may consume at most
 * {@code limit} permits per window. It is cheap (one counter per key) but allows up to twice the
 * limit across a window boundary: a burst at the end of one window followed by another at the start
 * of the next. Use the token bucket when that matters.
 *
 * <p>Like the token bucket, updates are compare-and-set loops on an immutable state object.
 */
public final class InMemoryFixedWindowRateLimiter implements RateLimiter {

    private final FixedWindowConfig config;
    private final TimeSource timeSource;
    private final long windowNanos;
    private final ConcurrentMap<String, AtomicReference<WindowState>> windows = new ConcurrentHashMap<>();
    private final IdleKeySweeper<AtomicReference<WindowState>> sweeper;

    public InMemoryFixedWindowRateLimiter(FixedWindowConfig config) {
        this(config, TimeSource.system());
    }

    public InMemoryFixedWindowRateLimiter(FixedWindowConfig config, TimeSource timeSource) {
        this.config = Objects.requireNonNull(config, "config");
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
        this.windowNanos = config.window().toNanos();
        this.sweeper = new IdleKeySweeper<>(
                windows,
                window -> window.get().windowIndex() < Math.floorDiv(this.timeSource.nanoTime(), windowNanos));
    }

    @Override
    public RateLimitResult tryAcquire(String key, long permits) {
        validate(key, permits);
        sweeper.onCall();

        AtomicReference<WindowState> window =
                windows.computeIfAbsent(key, k -> new AtomicReference<>(new WindowState(Long.MIN_VALUE, 0)));

        while (true) {
            long now = timeSource.nanoTime();
            long windowIndex = Math.floorDiv(now, windowNanos);
            WindowState current = window.get();
            long used = current.windowIndex() == windowIndex ? current.used() : 0;

            if (used + permits > config.limit()) {
                return RateLimitResult.rejected(config.limit(), config.limit() - used, untilNextWindow(now));
            }
            WindowState next = new WindowState(windowIndex, used + permits);
            if (window.compareAndSet(current, next)) {
                return RateLimitResult.allowed(config.limit(), config.limit() - next.used());
            }
            // Another thread updated the counter first: retry with the fresh state.
        }
    }

    private Duration untilNextWindow(long now) {
        long nextWindowStart = (Math.floorDiv(now, windowNanos) + 1) * windowNanos;
        return Duration.ofNanos(nextWindowStart - now);
    }

    /**
     * @return number of keys currently tracked (useful for metrics and tests)
     */
    public int trackedKeys() {
        return windows.size();
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

    private record WindowState(long windowIndex, long used) {
    }
}
