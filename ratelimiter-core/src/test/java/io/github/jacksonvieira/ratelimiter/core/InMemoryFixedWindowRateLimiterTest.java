package io.github.jacksonvieira.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryFixedWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(10);

    private ManualTimeSource time;
    private InMemoryFixedWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        time = new ManualTimeSource();
        limiter = new InMemoryFixedWindowRateLimiter(new FixedWindowConfig(3, WINDOW), time);
    }

    @Test
    void allowsUpToTheLimitThenRejectsUntilTheNextWindow() {
        for (int i = 2; i >= 0; i--) {
            RateLimitResult result = limiter.tryAcquire("a");
            assertTrue(result.allowed());
            assertEquals(i, result.remaining());
        }

        RateLimitResult rejected = limiter.tryAcquire("a");
        assertFalse(rejected.allowed());
        assertEquals(0, rejected.remaining());
        assertTrue(rejected.retryAfter().compareTo(WINDOW) <= 0);
        assertTrue(rejected.retryAfter().isPositive());
    }

    @Test
    void theCounterResetsWhenANewWindowStarts() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        assertFalse(limiter.tryAcquire("a").allowed());

        time.advance(WINDOW);

        RateLimitResult result = limiter.tryAcquire("a");
        assertTrue(result.allowed());
        assertEquals(2, result.remaining());
    }

    @Test
    void retryAfterIsTheTimeLeftInTheCurrentWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }

        Duration retryAfter = limiter.tryAcquire("a").retryAfter();
        time.advance(retryAfter);

        assertTrue(limiter.tryAcquire("a").allowed());
    }

    @Test
    void allowsABurstOnEachSideOfAWindowBoundary() {
        // Known trade-off of the algorithm: 2 x limit within a short time span around the boundary.
        // The manual clock starts at 1 s: move it to 1 ns before the end of the first window.
        time.advance(WINDOW.minusSeconds(1).minusNanos(1));
        int allowed = 0;
        for (int i = 0; i < 3; i++) {
            allowed += limiter.tryAcquire("a").allowed() ? 1 : 0;
        }
        time.advance(Duration.ofNanos(2));
        for (int i = 0; i < 3; i++) {
            allowed += limiter.tryAcquire("a").allowed() ? 1 : 0;
        }
        assertEquals(6, allowed);
    }

    @Test
    void multiplePermitsAreAllOrNothing() {
        assertTrue(limiter.tryAcquire("a", 2).allowed());

        RateLimitResult rejected = limiter.tryAcquire("a", 2);
        assertFalse(rejected.allowed());
        assertEquals(1, rejected.remaining());
        assertTrue(limiter.tryAcquire("a", 1).allowed());
    }

    @Test
    void keysHaveIndependentWindows() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }

        assertFalse(limiter.tryAcquire("a").allowed());
        assertTrue(limiter.tryAcquire("b").allowed());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(" "));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("a", 0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("a", 4));
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowConfig(0, WINDOW));
        assertThrows(IllegalArgumentException.class, () -> new FixedWindowConfig(1, Duration.ZERO));
    }

    @Test
    void concurrentRequestsNeverExceedTheLimit() throws Exception {
        InMemoryFixedWindowRateLimiter shared =
                new InMemoryFixedWindowRateLimiter(new FixedWindowConfig(50, WINDOW), time);
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    int allowed = 0;
                    for (int i = 0; i < 20; i++) {
                        allowed += shared.tryAcquire("hot").allowed() ? 1 : 0;
                    }
                    return allowed;
                }));
            }
            start.countDown();

            int total = 0;
            for (Future<Integer> result : results) {
                total += result.get();
            }
            assertEquals(50, total);
        }
    }
}
