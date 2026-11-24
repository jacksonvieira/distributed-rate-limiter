package io.github.jacksonvieira.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CircuitBreakerRateLimiterTest {

    private static final Duration OPEN = Duration.ofSeconds(30);
    private static final RateLimitResult OK = new RateLimitResult(true, 5, 4, Duration.ZERO);
    private static final RateLimitResult FALLBACK = new RateLimitResult(true, 2, 1, Duration.ZERO);

    private ManualTimeSource time;
    private boolean primaryDown;
    private final AtomicInteger primaryCalls = new AtomicInteger();
    private final AtomicInteger fallbackCalls = new AtomicInteger();
    private CircuitBreakerRateLimiter limiter;

    @BeforeEach
    void setUp() {
        time = new ManualTimeSource();
        primaryDown = false;
        RateLimiter primary = (key, permits) -> {
            primaryCalls.incrementAndGet();
            if (primaryDown) {
                throw new IllegalStateException("redis down");
            }
            return OK;
        };
        RateLimiter fallback = (key, permits) -> {
            fallbackCalls.incrementAndGet();
            return FALLBACK;
        };
        limiter = new CircuitBreakerRateLimiter(primary, fallback, 2, OPEN, time);
    }

    @Test
    void usesThePrimaryWhileHealthy() {
        assertEquals(OK, limiter.tryAcquire("a"));
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void fallsBackOnFailureAndOpensAfterTheThreshold() {
        primaryDown = true;

        assertEquals(FALLBACK, limiter.tryAcquire("a"));
        assertEquals(FALLBACK, limiter.tryAcquire("a"));
        assertEquals(2, primaryCalls.get());

        assertEquals(FALLBACK, limiter.tryAcquire("a"));
        assertEquals(2, primaryCalls.get(), "open circuit must not call the primary");
    }

    @Test
    void aSuccessResetsTheFailureCount() {
        primaryDown = true;
        limiter.tryAcquire("a");
        primaryDown = false;
        limiter.tryAcquire("a");
        primaryDown = true;
        limiter.tryAcquire("a");

        primaryDown = false;
        assertEquals(OK, limiter.tryAcquire("a"));
    }

    @Test
    void probesAfterTheOpenDurationAndClosesOnSuccess() {
        primaryDown = true;
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        time.advance(OPEN);
        primaryDown = false;

        assertEquals(OK, limiter.tryAcquire("a"));
        assertEquals(OK, limiter.tryAcquire("a"));
    }

    @Test
    void failedProbeReopensTheCircuit() {
        primaryDown = true;
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");

        time.advance(OPEN);
        assertEquals(FALLBACK, limiter.tryAcquire("a"));
        int callsAfterProbe = primaryCalls.get();

        assertEquals(FALLBACK, limiter.tryAcquire("a"));
        assertEquals(callsAfterProbe, primaryCalls.get());
    }

    @Test
    void invalidRequestsAreNotTreatedAsFailures() {
        RateLimiter strict = (key, permits) -> {
            throw new IllegalArgumentException("too many permits");
        };
        RateLimiter fallback = (key, permits) -> FALLBACK;
        CircuitBreakerRateLimiter breaker = new CircuitBreakerRateLimiter(strict, fallback, 1, OPEN, time);

        assertThrows(IllegalArgumentException.class, () -> breaker.tryAcquire("a", 99));
        assertThrows(IllegalArgumentException.class, () -> breaker.tryAcquire("a", 99));
    }

    @Test
    void rejectsInvalidConfiguration() {
        RateLimiter any = (key, permits) -> OK;
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreakerRateLimiter(any, any, 0, OPEN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CircuitBreakerRateLimiter(any, any, 1, Duration.ZERO));
    }
}
