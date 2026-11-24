package io.github.jacksonvieira.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IdleKeyEvictionTest {

    private static final Duration WINDOW = Duration.ofSeconds(10);
    private static final int KEYS = 100;

    record Subject(String name, Function<TimeSource, RateLimiter> factory, ToIntFunction<RateLimiter> trackedKeys) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Subject[] limiters() {
        return new Subject[] {
            new Subject(
                    "token bucket",
                    t -> new InMemoryTokenBucketRateLimiter(new TokenBucketConfig(5, 1, Duration.ofSeconds(1)), t),
                    l -> ((InMemoryTokenBucketRateLimiter) l).trackedKeys()),
            new Subject(
                    "fixed window",
                    t -> new InMemoryFixedWindowRateLimiter(new FixedWindowConfig(5, WINDOW), t),
                    l -> ((InMemoryFixedWindowRateLimiter) l).trackedKeys()),
            new Subject(
                    "sliding window log",
                    t -> new InMemorySlidingWindowLogRateLimiter(new SlidingWindowConfig(5, WINDOW), t),
                    l -> ((InMemorySlidingWindowLogRateLimiter) l).trackedKeys()),
            new Subject(
                    "sliding window counter",
                    t -> new InMemorySlidingWindowCounterRateLimiter(new SlidingWindowConfig(5, WINDOW), t),
                    l -> ((InMemorySlidingWindowCounterRateLimiter) l).trackedKeys())
        };
    }

    private static void touchEveryKeyThenSweep(RateLimiter limiter, ManualTimeSource time) {
        for (int i = 0; i < KEYS; i++) {
            limiter.tryAcquire("old-" + i);
        }
        time.advance(WINDOW.multipliedBy(3));
        for (long i = 0; i < IdleKeySweeper.DEFAULT_INTERVAL; i++) {
            limiter.tryAcquire("active");
        }
    }

    @ParameterizedTest
    @MethodSource("limiters")
    void dropsKeysThatHaveBeenIdleLongEnough(Subject subject) {
        ManualTimeSource time = new ManualTimeSource();
        RateLimiter limiter = subject.factory().apply(time);

        touchEveryKeyThenSweep(limiter, time);

        assertEquals(1, subject.trackedKeys().applyAsInt(limiter));
    }

    @ParameterizedTest
    @MethodSource("limiters")
    void keysRecoverTheirFullLimitAfterBeingEvicted(Subject subject) {
        ManualTimeSource time = new ManualTimeSource();
        RateLimiter limiter = subject.factory().apply(time);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("old-0");
        }
        assertFalse(limiter.tryAcquire("old-0").allowed());

        touchEveryKeyThenSweep(limiter, time);

        assertEquals(4, limiter.tryAcquire("old-0").remaining());
    }

    @ParameterizedTest
    @MethodSource("limiters")
    void keepsKeysThatAreStillInUse(Subject subject) {
        ManualTimeSource time = new ManualTimeSource();
        RateLimiter limiter = subject.factory().apply(time);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("busy");
        }

        for (long i = 0; i < IdleKeySweeper.DEFAULT_INTERVAL; i++) {
            limiter.tryAcquire("other");
        }

        assertEquals(2, subject.trackedKeys().applyAsInt(limiter));
        assertFalse(limiter.tryAcquire("busy").allowed());
    }

    @Test
    void rejectsAnInvalidSweepInterval() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new IdleKeySweeper<>(new java.util.concurrent.ConcurrentHashMap<String, Object>(), v -> true, 0));
    }
}
