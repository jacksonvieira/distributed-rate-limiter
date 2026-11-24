package io.github.jacksonvieira.ratelimiter.gateway;

import java.util.List;

/**
 * Outbound port: where the gateway fetches its current rules from at runtime.
 */
interface RuleSource {

    /**
     * @return the current rules in evaluation order
     * @throws RuntimeException if the rules cannot be fetched; callers keep using the rules they already have
     */
    List<GatewayProperties.Rule> load();
}
