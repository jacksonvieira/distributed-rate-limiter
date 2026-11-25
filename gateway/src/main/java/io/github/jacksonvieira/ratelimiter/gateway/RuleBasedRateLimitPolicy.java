package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Applies the first matching rule's limiter, or the default one when no rule matches.
 *
 * <p>Each rule counts on its own: its keys are prefixed with the rule name, so the same client has
 * independent counters under different rules.
 */
final class RuleBasedRateLimitPolicy implements RateLimitPolicy {

    private final List<CompiledRule> rules;
    private final RateLimitPolicy fallback;
    private final Function<ServerHttpRequest, String> planResolver;

    RuleBasedRateLimitPolicy(
            List<CompiledRule> rules, RateLimitPolicy fallback, Function<ServerHttpRequest, String> planResolver) {
        this.rules = List.copyOf(rules);
        this.fallback = fallback;
        this.planResolver = planResolver;
    }

    @Override
    public Selection select(ServerHttpRequest request) {
        String path = request.getPath().value();
        String plan = planResolver.apply(request);
        for (CompiledRule compiled : rules) {
            if (compiled.rule().matches(path, plan)) {
                String key = compiled.rule().name() + ":" + compiled.keyResolver().resolve(request);
                return new Selection(compiled.limiter(), key, compiled.rule().name());
            }
        }
        return fallback.select(request);
    }

    record CompiledRule(GatewayProperties.Rule rule, RateLimiter limiter, KeyResolver keyResolver) {}
}
