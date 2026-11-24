package io.github.jacksonvieira.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemorySlidingWindowCounterRateLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(10);

    private ManualTimeSource time;
    private InMemorySlidingWindowCounterRateLimiter limiter;

    @BeforeEach
    void setUp() {
        time = new ManualTimeSource();
        limiter = new InMemorySlidingWindowCounterRateLimiter(new SlidingWindowConfig(10, WINDOW), time);
        // ManualTimeSource starts at 1s; move to the start of a window so fractions are easy to reason about.
        time.advance(Duration.ofSeconds(9));
    }

    @Test
    void allowsUpToTheLimitThenRejects() {
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("a").allowed());
        }
        RateLimitResult rejected = limiter.tryAcquire("a");
        assertFalse(rejected.allowed());
        assertEquals(0, rejected.remaining());
    }

    @Test
    void previousWindowFadesLinearly() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a");
        }
        // Halfway into the next window the previous 10 weigh 5, so exactly 5 more fit.
        time.advance(Duration.ofSeconds(15));
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("a").allowed(), "request " + i);
        }
        assertFalse(limiter.tryAcquire("a").allowed());
    }

    @Test
    void doesNotAllowAFullBurstAtTheWindowBoundary() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a");
        }
        time.advance(Duration.ofSeconds(10));
        assertFalse(limiter.tryAcquire("a").allowed());
    }

    @Test
    void retryAfterPointsToWhenTheEstimateFitsAgain() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a");
        }
        time.advance(Duration.ofSeconds(10));
        RateLimitResult rejected = limiter.tryAcquire("a");
        assertEquals(Duration.ofSeconds(1), rejected.retryAfter());

        time.advance(rejected.retryAfter());
        assertTrue(limiter.tryAcquire("a").allowed());
    }

    @Test
    void forgetsEverythingAfterAnIdleWindow() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a");
        }
        time.advance(Duration.ofSeconds(25));
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("a").allowed());
        }
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a");
        }
        assertTrue(limiter.tryAcquire("b").allowed());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(" ", 1));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("a", 0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("a", 11));
    }
}
