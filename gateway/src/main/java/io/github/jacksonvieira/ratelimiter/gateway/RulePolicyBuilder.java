package io.github.jacksonvieira.ratelimiter.gateway;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns rules into a {@link RateLimitPolicy}. A rule that is unchanged since the previous build keeps its
 * limiter, so reloading does not reset the counters of rules nobody touched.
 */
final class RulePolicyBuilder {

    private final GatewayProperties properties;
    private final RateLimiterFactory factory;
    private final RateLimitPolicy fallback;
    private Map<GatewayProperties.Rule, RuleBasedRateLimitPolicy.CompiledRule> previous = Map.of();

    RulePolicyBuilder(GatewayProperties properties, RateLimiterFactory factory, RateLimitPolicy fallback) {
        this.properties = properties;
        this.factory = factory;
        this.fallback = fallback;
    }

    synchronized RateLimitPolicy build(List<GatewayProperties.Rule> rules) {
        if (rules.isEmpty()) {
            previous = Map.of();
            return fallback;
        }
        Map<GatewayProperties.Rule, RuleBasedRateLimitPolicy.CompiledRule> current = new HashMap<>();
        for (GatewayProperties.Rule rule : rules) {
            current.put(rule, previous.containsKey(rule) ? previous.get(rule) : compile(rule));
        }
        previous = current;
        return new RuleBasedRateLimitPolicy(
                rules.stream().map(current::get).toList(), fallback, properties.planHeader());
    }

    private RuleBasedRateLimitPolicy.CompiledRule compile(GatewayProperties.Rule rule) {
        GatewayProperties.RateLimit effective = rule.applyTo(properties.rateLimit());
        return new RuleBasedRateLimitPolicy.CompiledRule(
                rule, factory.create(effective, rule.name()), GatewayConfiguration.keyResolverFor(effective));
    }
}
