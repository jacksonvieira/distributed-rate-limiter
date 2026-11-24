package io.github.jacksonvieira.ratelimiter.gateway;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;

/**
 * Counts decisions as {@code ratelimiter.decisions{rule, outcome}}; Prometheus exposes it as
 * {@code ratelimiter_decisions_total}.
 */
final class MicrometerRateLimitObserver implements RateLimitObserver {

    static final String METRIC = "ratelimiter.decisions";

    private final MeterRegistry registry;

    MicrometerRateLimitObserver(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onDecision(String rule, Outcome outcome) {
        registry.counter(METRIC, "rule", rule, "outcome", outcome.name().toLowerCase(Locale.ROOT)).increment();
    }
}
