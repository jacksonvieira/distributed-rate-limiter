package io.github.jacksonvieira.ratelimiter.core;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Time source for tests: time only moves when the test advances it.
 */
final class ManualTimeSource implements TimeSource {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

    @Override
    public long nanoTime() {
        return nanos.get();
    }

    void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }
}
