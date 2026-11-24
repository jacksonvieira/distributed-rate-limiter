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

class InMemorySlidingWindowLogRateLimiterTest {

    private static final Duration WINDOW = Duration.ofSeconds(10);

    private ManualTimeSource time;
    private InMemorySlidingWindowLogRateLimiter limiter;

    @BeforeEach
    void setUp() {
        time = new ManualTimeSource();
        limiter = new InMemorySlidingWindowLogRateLimiter(new SlidingWindowConfig(3, WINDOW), time);
    }

    @Test
    void allowsUpToTheLimitThenRejects() {
        for (int i = 2; i >= 0; i--) {
            RateLimitResult result = limiter.tryAcquire("a");
            assertTrue(result.allowed());
            assertEquals(i, result.remaining());
        }
        RateLimitResult rejected = limiter.tryAcquire("a");
        assertFalse(rejected.allowed());
        assertEquals(WINDOW, rejected.retryAfter());
    }

    @Test
    void permitsFreeUpAsTheirTimestampsLeaveTheWindow() {
        limiter.tryAcquire("a");
        time.advance(Duration.ofSeconds(4));
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        assertFalse(limiter.tryAcquire("a").allowed());

        time.advance(Duration.ofSeconds(6));

        // Only the first permit has expired.
        assertTrue(limiter.tryAcquire("a").allowed());
        assertFalse(limiter.tryAcquire("a").allowed());
    }

    @Test
    void retryAfterPointsAtTheMomentEnoughPermitsExpire() {
        limiter.tryAcquire("a");
        time.advance(Duration.ofSeconds(4));
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        Duration retryAfter = limiter.tryAcquire("a").retryAfter();
        assertEquals(Duration.ofSeconds(6), retryAfter);
        time.advance(retryAfter);
        assertTrue(limiter.tryAcquire("a").allowed());
    }

    @Test
    void hasNoBurstAcrossWindowBoundaries() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        time.advance(WINDOW.minusNanos(1));

        assertFalse(limiter.tryAcquire("a").allowed());
    }

    @Test
    void multiplePermitsAreAllOrNothing() {
        assertTrue(limiter.tryAcquire("a", 2).allowed());
        RateLimitResult rejected = limiter.tryAcquire("a", 2);
        assertFalse(rejected.allowed());
        assertEquals(1, rejected.remaining());
        assertTrue(limiter.tryAcquire("a").allowed());
    }

    @Test
    void keysAreIndependentAndArgumentsValidated() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        assertTrue(limiter.tryAcquire("b").allowed());
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(" "));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("a", 4));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowConfig(0, WINDOW));
    }

    @Test
    void concurrentRequestsNeverExceedTheLimit() throws Exception {
        InMemorySlidingWindowLogRateLimiter shared =
                new InMemorySlidingWindowLogRateLimiter(new SlidingWindowConfig(50, WINDOW), time);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
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
