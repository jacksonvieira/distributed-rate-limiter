package io.github.jacksonvieira.ratelimiter.admin.domain;

/** Rate limiting algorithm a rule can select; mirrors the gateway's algorithms. */
public enum Algorithm {
    /** Tokens refill continuously up to the capacity. */
    TOKEN_BUCKET,
    /** Counts requests in consecutive fixed time windows. */
    FIXED_WINDOW,
    /** Keeps a timestamp per request inside the sliding window. */
    SLIDING_WINDOW_LOG,
    /** Approximates a sliding window by weighting the previous window's count. */
    SLIDING_WINDOW_COUNTER
}
