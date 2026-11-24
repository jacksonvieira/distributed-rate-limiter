package io.github.jacksonvieira.ratelimiter.admin.domain;

public enum Algorithm {
    TOKEN_BUCKET,
    FIXED_WINDOW,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER
}
