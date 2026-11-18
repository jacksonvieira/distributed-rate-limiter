package io.github.jacksonvieira.ratelimiter.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTokenBucketRateLimiterTest {

    private ManualTimeSource time;
    private InMemoryTokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        time = new ManualTimeSource();
        // Bursts of up to 5 requests, sustained rate of 1 request per second.
        limiter = new InMemoryTokenBucketRateLimiter(
                new TokenBucketConfig(5, 1, Duration.ofSeconds(1)), time);
    }

    @Nested
    @DisplayName("burst capacity")
    class BurstCapacity {

        @Test
        void allowsRequestsUpToCapacity() {
            for (int i = 4; i >= 0; i--) {
                RateLimitResult result = limiter.tryAcquire("client-a");
                assertTrue(result.allowed());
                assertEquals(5, result.limit());
                assertEquals(i, result.remaining());
                assertEquals(Duration.ZERO, result.retryAfter());
            }
        }

        @Test
        void rejectsOnceTheBucketIsEmpty() {
            drain("client-a", 5);

            RateLimitResult result = limiter.tryAcquire("client-a");

            assertFalse(result.allowed());
            assertEquals(0, result.remaining());
            assertEquals(Duration.ofSeconds(1), result.retryAfter());
        }

        @Test
        void consumesSeveralPermitsAtomically() {
            assertTrue(limiter.tryAcquire("client-a", 3).allowed());

            RateLimitResult result = limiter.tryAcquire("client-a", 3);

            assertFalse(result.allowed(), "only 2 tokens left, 3 requested");
            assertEquals(2, result.remaining(), "a rejected request must not consume tokens");
            assertTrue(limiter.tryAcquire("client-a", 2).allowed());
        }
    }

    @Nested
    @DisplayName("refill")
    class Refill {

        @Test
        void refillsAtTheConfiguredRate() {
            drain("client-a", 5);

            time.advance(Duration.ofSeconds(1));
            assertTrue(limiter.tryAcquire("client-a").allowed());
            assertFalse(limiter.tryAcquire("client-a").allowed());
        }

        @Test
        void refillsContinuouslyNotInSteps() {
            drain("client-a", 5);

            time.advance(Duration.ofMillis(500));
            RateLimitResult result = limiter.tryAcquire("client-a");

            assertFalse(result.allowed());
            assertEquals(Duration.ofMillis(500), result.retryAfter(), "half a token is already there");
        }

        @Test
        void neverRefillsAboveCapacity() {
            drain("client-a", 5);

            time.advance(Duration.ofHours(1));

            drain("client-a", 5);
            assertFalse(limiter.tryAcquire("client-a").allowed());
        }

        @Test
        void ignoresTimeGoingBackwards() {
            drain("client-a", 5);

            time.advance(Duration.ofSeconds(-10));
            assertFalse(limiter.tryAcquire("client-a").allowed());

            // Back to the original time: no tokens may have been gained by the clock jump.
            time.advance(Duration.ofSeconds(10));
            assertFalse(limiter.tryAcquire("client-a").allowed());
        }
    }

    @Nested
    @DisplayName("keys")
    class Keys {

        @Test
        void keepsAnIndependentBucketPerKey() {
            drain("client-a", 5);

            assertFalse(limiter.tryAcquire("client-a").allowed());
            assertTrue(limiter.tryAcquire("client-b").allowed());
            assertEquals(2, limiter.trackedKeys());
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        void rejectsBlankKeys() {
            assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(" "));
            assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(null));
        }

        @Test
        void rejectsInvalidPermitCounts() {
            assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("client-a", 0));
            assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("client-a", 6));
        }

        @Test
        void rejectsInvalidConfiguration() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TokenBucketConfig(0, 1, Duration.ofSeconds(1)));
            assertThrows(IllegalArgumentException.class,
                    () -> new TokenBucketConfig(1, 0, Duration.ofSeconds(1)));
            assertThrows(IllegalArgumentException.class,
                    () -> new TokenBucketConfig(1, 1, Duration.ZERO));
        }
    }

    @Nested
    @DisplayName("concurrency")
    class Concurrency {

        @Test
        void neverGrantsMorePermitsThanCapacityUnderContention() throws Exception {
            int capacity = 1_000;
            int threads = 32;
            int attemptsPerThread = 500;
            InMemoryTokenBucketRateLimiter contended = new InMemoryTokenBucketRateLimiter(
                    new TokenBucketConfig(capacity, 1, Duration.ofDays(1)), time);

            AtomicInteger granted = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                for (int t = 0; t < threads; t++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        for (int i = 0; i < attemptsPerThread; i++) {
                            if (contended.tryAcquire("shared-key").allowed()) {
                                granted.incrementAndGet();
                            }
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }
            }

            assertEquals(capacity, granted.get());
        }
    }

    private void drain(String key, int permits) {
        for (int i = 0; i < permits; i++) {
            assertTrue(limiter.tryAcquire(key).allowed(), "expected permit " + (i + 1) + " to be granted");
        }
    }
}
