package io.github.jacksonvieira.ratelimiter.gateway;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * A policy whose rules can be swapped while requests are in flight: each request sees either the old or the
 * new rule set, never a mix.
 */
final class ReloadableRateLimitPolicy implements RateLimitPolicy {

    private final RulePolicyBuilder builder;
    private final AtomicReference<RateLimitPolicy> current;
    private List<GatewayProperties.Rule> loaded;

    ReloadableRateLimitPolicy(RulePolicyBuilder builder, List<GatewayProperties.Rule> initialRules) {
        this.builder = builder;
        this.loaded = List.copyOf(initialRules);
        this.current = new AtomicReference<>(builder.build(loaded));
    }

    @Override
    public Selection select(ServerHttpRequest request) {
        return current.get().select(request);
    }

    /**
     * @return whether the rules differed from the ones in force and were applied
     */
    synchronized boolean reload(List<GatewayProperties.Rule> rules) {
        if (loaded.equals(rules)) {
            return false;
        }
        current.set(builder.build(rules));
        loaded = List.copyOf(rules);
        return true;
    }
}
