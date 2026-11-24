package io.github.jacksonvieira.ratelimiter.gateway;

/**
 * Receives every rate limiting decision, so metrics can be recorded without the filter knowing how.
 */
@FunctionalInterface
public interface RateLimitObserver {

    RateLimitObserver NOOP = (rule, outcome) -> {};

    enum Outcome {
        /** The limiter let the request through. */
        ALLOWED,
        /** The limiter rejected the request with {@code 429}. */
        REJECTED,
        /** The limiter itself failed; the configured failure mode decided what happened. */
        LIMITER_FAILED
    }

    void onDecision(String rule, Outcome outcome);
}
