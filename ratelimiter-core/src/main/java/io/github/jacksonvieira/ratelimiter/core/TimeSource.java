package io.github.jacksonvieira.ratelimiter.core;

/**
 * Monotonic time source, abstracted so algorithms can be tested without sleeping.
 */
@FunctionalInterface
public interface TimeSource {

    /**
     * @return the current value of a monotonic clock, in nanoseconds
     */
    long nanoTime();

    /**
     * @return a time source backed by {@link System#nanoTime()}
     */
    static TimeSource system() {
        return System::nanoTime;
    }
}
