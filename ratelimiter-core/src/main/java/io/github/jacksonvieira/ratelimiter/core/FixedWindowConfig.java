package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration of a fixed window counter: at most {@code limit} permits per {@code window}.
 *
 * @param limit  maximum number of permits granted in one window
 * @param window length of a window
 */
public record FixedWindowConfig(long limit, Duration window) {

    public FixedWindowConfig {
        Objects.requireNonNull(window, "window");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1, got " + limit);
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive, got " + window);
        }
    }
}
