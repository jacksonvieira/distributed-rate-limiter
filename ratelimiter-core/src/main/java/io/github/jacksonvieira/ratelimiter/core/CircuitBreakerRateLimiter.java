package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Protects a remote limiter (e.g. Redis) with a circuit breaker and a local fallback.
 *
 * <p>While the circuit is closed every call goes to the {@code primary}. After {@code failureThreshold}
 * consecutive failures the circuit opens and calls go straight to the {@code fallback}, without waiting for
 * the primary to time out. Once {@code openDuration} has elapsed one call is let through to probe the primary:
 * success closes the circuit, failure reopens it.
 *
 * <p>The fallback only sees the traffic of this instance, so during an outage the limit is enforced per
 * instance instead of globally: degraded, but still protective.
 */
public final class CircuitBreakerRateLimiter implements RateLimiter {

    private final RateLimiter primary;
    private final RateLimiter fallback;
    private final int failureThreshold;
    private final long openNanos;
    private final TimeSource time;

    private final Object lock = new Object();
    private int consecutiveFailures;
    private boolean open;
    private boolean probing;
    private long openedAt;

    public CircuitBreakerRateLimiter(
            RateLimiter primary, RateLimiter fallback, int failureThreshold, Duration openDuration) {
        this(primary, fallback, failureThreshold, openDuration, TimeSource.system());
    }

    CircuitBreakerRateLimiter(
            RateLimiter primary,
            RateLimiter fallback,
            int failureThreshold,
            Duration openDuration,
            TimeSource time) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.time = Objects.requireNonNull(time, "time");
        Objects.requireNonNull(openDuration, "openDuration");
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be at least 1, got " + failureThreshold);
        }
        if (openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalArgumentException("openDuration must be positive, got " + openDuration);
        }
        this.failureThreshold = failureThreshold;
        this.openNanos = openDuration.toNanos();
    }

    @Override
    public RateLimitResult tryAcquire(String key, long permits) {
        if (!mayCallPrimary()) {
            return fallback.tryAcquire(key, permits);
        }
        RateLimitResult result;
        try {
            result = primary.tryAcquire(key, permits);
        } catch (IllegalArgumentException invalidRequest) {
            releaseProbe();
            throw invalidRequest;
        } catch (RuntimeException failure) {
            recordFailure();
            return fallback.tryAcquire(key, permits);
        }
        recordSuccess();
        return result;
    }

    private boolean mayCallPrimary() {
        synchronized (lock) {
            if (!open) {
                return true;
            }
            if (!probing && time.nanoTime() - openedAt >= openNanos) {
                probing = true;
                return true;
            }
            return false;
        }
    }

    private void recordSuccess() {
        synchronized (lock) {
            consecutiveFailures = 0;
            open = false;
            probing = false;
        }
    }

    private void recordFailure() {
        synchronized (lock) {
            consecutiveFailures++;
            if (open || consecutiveFailures >= failureThreshold) {
                open = true;
                openedAt = time.nanoTime();
            }
            probing = false;
        }
    }

    private void releaseProbe() {
        synchronized (lock) {
            probing = false;
        }
    }
}
