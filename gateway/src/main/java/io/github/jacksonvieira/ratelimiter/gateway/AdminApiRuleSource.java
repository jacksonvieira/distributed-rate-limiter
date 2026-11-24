package io.github.jacksonvieira.ratelimiter.gateway;

import java.time.Duration;
import java.util.List;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Fetches rules from the admin API's {@code GET /api/rules}.
 */
final class AdminApiRuleSource implements RuleSource {

    private final WebClient client;
    private final Duration timeout;

    AdminApiRuleSource(WebClient client, Duration timeout) {
        this.client = client;
        this.timeout = timeout;
    }

    @Override
    public List<GatewayProperties.Rule> load() {
        List<RuleDto> dtos = client.get()
                .uri("/api/rules")
                .retrieve()
                .bodyToFlux(RuleDto.class)
                .collectList()
                .block(timeout);
        if (dtos == null) {
            throw new IllegalStateException("admin API returned no body");
        }
        return dtos.stream().map(RuleDto::toRule).toList();
    }

    /** Wire format of a rule in the admin API; unknown fields such as {@code id} are ignored. */
    record RuleDto(
            String name,
            String pathPrefix,
            String plan,
            Long capacity,
            Long refillTokens,
            String refillPeriod,
            GatewayProperties.Algorithm algorithm,
            GatewayProperties.KeyStrategy key,
            String keyHeader) {

        GatewayProperties.Rule toRule() {
            return new GatewayProperties.Rule(
                    name,
                    pathPrefix,
                    plan,
                    capacity,
                    refillTokens,
                    refillPeriod == null ? null : Duration.parse(refillPeriod),
                    algorithm,
                    key,
                    keyHeader);
        }
    }
}
