package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration of a sliding window: at most {@code limit} permits in any interval of length {@code window}.
 *
 * @param limit  maximum number of permits granted within any window-long interval
 * @param window length of the sliding window
 */
public record SlidingWindowConfig(long limit, Duration window) {

    public SlidingWindowConfig {
        Objects.requireNonNull(window, "window");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive, got " + window);
        }
    }
}
