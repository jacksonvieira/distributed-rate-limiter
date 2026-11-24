package io.github.jacksonvieira.ratelimiter.gateway;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Pulls rules from a {@link RuleSource} and applies them. A failed fetch keeps the rules already in force.
 */
@Slf4j
final class RuleRefresher {

    private final RuleSource source;
    private final ReloadableRateLimitPolicy policy;

    RuleRefresher(RuleSource source, ReloadableRateLimitPolicy policy) {
        this.source = source;
        this.policy = policy;
    }

    void refresh() {
        try {
            List<GatewayProperties.Rule> rules = source.load();
            if (policy.reload(rules)) {
                log.info("Reloaded {} rules", rules.size());
            }
        } catch (RuntimeException e) {
            log.warn("Could not refresh rules, keeping the current ones: {}", e.toString());
        }
    }
}
