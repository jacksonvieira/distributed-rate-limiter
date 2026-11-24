package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory sliding window counter: keeps one counter for the current fixed window and one for the
 * previous window, and estimates the number of requests in the sliding window as
 * {@code previous * (1 - elapsedFraction) + current}.
 *
 * <p>It needs O(1) memory per key and smooths the boundary burst of the fixed window, but the estimate
 * assumes requests in the previous window were evenly spread, so it is approximate.
 */
public final class InMemorySlidingWindowCounterRateLimiter implements RateLimiter {

    private final SlidingWindowConfig config;
    private final TimeSource timeSource;
    private final long windowNanos;
    private final ConcurrentMap<String, Counters> counters = new ConcurrentHashMap<>();
    private final IdleKeySweeper<Counters> sweeper;

    public InMemorySlidingWindowCounterRateLimiter(SlidingWindowConfig config) {
        this(config, TimeSource.system());
    }

    public InMemorySlidingWindowCounterRateLimiter(SlidingWindowConfig config, TimeSource timeSource) {
        this.config = Objects.requireNonNull(config, "config");
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
        this.windowNanos = config.window().toNanos();
        this.sweeper = new IdleKeySweeper<>(counters, this::isIdle);
    }

    @Override
    public RateLimitResult tryAcquire(String key, long permits) {
        validate(key, permits);
        sweeper.onCall();

        Counters state = counters.computeIfAbsent(key, k -> new Counters());
        synchronized (state) {
            long now = timeSource.nanoTime();
            long index = Math.floorDiv(now, windowNanos);
            state.roll(index);

            double elapsed = (double) Math.floorMod(now, windowNanos) / windowNanos;
            double estimated = state.previous * (1.0 - elapsed) + state.current;
            long limit = config.limit();

            if (estimated + permits > limit) {
                long remaining = Math.max(0L, (long) Math.floor(limit - estimated));
                return RateLimitResult.rejected(limit, remaining, retryAfter(state, permits, elapsed));
            }
            state.current += permits;
            long remaining = Math.max(0L, (long) Math.floor(limit - (estimated + permits)));
            return RateLimitResult.allowed(limit, remaining);
        }
    }

    // The estimate only decreases as the previous window's weight fades, so the wait is the time until
    // previous * (1 - f) <= limit - permits - current, possibly after the windows roll over.
    private Duration retryAfter(Counters state, long permits, double elapsed) {
        long slack = config.limit() - permits - state.current;
        double waitFraction;
        if (slack >= 0 && state.previous > 0) {
            waitFraction = (1.0 - (double) slack / state.previous) - elapsed;
        } else {
            double untilNextWindow = 1.0 - elapsed;
            long slackNext = config.limit() - permits;
            double fade = state.current > 0 ? 1.0 - (double) slackNext / state.current : 0.0;
            waitFraction = untilNextWindow + Math.max(0.0, fade);
        }
        return Duration.ofNanos(Math.max(1L, (long) Math.ceil(waitFraction * windowNanos)));
    }

    // Two whole windows without use leave both counters at zero, like a new key.
    private boolean isIdle(Counters state) {
        synchronized (state) {
            return state.index != Long.MIN_VALUE
                    && Math.floorDiv(timeSource.nanoTime(), windowNanos) > state.index + 1;
        }
    }

    /**
     * @return number of keys currently tracked (useful for metrics and tests)
     */
    public int trackedKeys() {
        return counters.size();
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

    private static final class Counters {
        private long index = Long.MIN_VALUE;
        private long previous;
        private long current;

        void roll(long newIndex) {
            if (index == Long.MIN_VALUE || newIndex > index + 1) {
                previous = 0;
                current = 0;
            } else if (newIndex == index + 1) {
                previous = current;
                current = 0;
            }
            index = newIndex;
        }
    }
}
