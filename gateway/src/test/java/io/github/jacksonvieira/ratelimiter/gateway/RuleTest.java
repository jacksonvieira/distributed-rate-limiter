package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleTest {

    private static GatewayProperties.Rule rule(String name, String pathPrefix, String plan) {
        return new GatewayProperties.Rule(name, pathPrefix, plan, null, null, null, null, null, null);
    }

    @Test
    void pathPrefixMatchesWholeSegmentsOnly() {
        GatewayProperties.Rule rule = rule("login", "/login/", null);

        assertThat(rule.matches("/login", null)).isTrue();
        assertThat(rule.matches("/login/reset", null)).isTrue();
        assertThat(rule.matches("/loginx", null)).isFalse();
    }

    @Test
    void planMustMatchWhenConfigured() {
        GatewayProperties.Rule rule = rule("premium", null, "premium");

        assertThat(rule.matches("/anything", "premium")).isTrue();
        assertThat(rule.matches("/anything", "free")).isFalse();
        assertThat(rule.matches("/anything", null)).isFalse();
    }

    @Test
    void inheritsWhatItDoesNotOverride() {
        GatewayProperties.RateLimit base = new GatewayProperties.RateLimit(
                5, 1, Duration.ofSeconds(1), GatewayProperties.KeyStrategy.IP, "X-API-Key",
                GatewayProperties.Algorithm.TOKEN_BUCKET, GatewayProperties.Store.MEMORY,
                GatewayProperties.FailureMode.FAIL_OPEN, new GatewayProperties.Redis("redis://x"));
        GatewayProperties.Rule rule = new GatewayProperties.Rule(
                "r", null, null, 50L, null, null, GatewayProperties.Algorithm.FIXED_WINDOW, null, null);

        GatewayProperties.RateLimit effective = rule.applyTo(base);

        assertThat(effective.capacity()).isEqualTo(50);
        assertThat(effective.algorithm()).isEqualTo(GatewayProperties.Algorithm.FIXED_WINDOW);
        assertThat(effective.refillPeriod()).isEqualTo(Duration.ofSeconds(1));
        assertThat(effective.key()).isEqualTo(GatewayProperties.KeyStrategy.IP);
    }

    @Test
    void rejectsInvalidRules() {
        assertThatThrownBy(() -> rule(" ", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rule("r", "login", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateRuleNames() {
        GatewayProperties.Route route = new GatewayProperties.Route(
                "/api", URI.create("http://localhost:1"), true, Duration.ofSeconds(1), Duration.ofSeconds(1));
        GatewayProperties.RateLimit limit = new GatewayProperties.RateLimit(
                5, 1, Duration.ofSeconds(1), null, null, null, null, null, null);

        assertThatThrownBy(() -> new GatewayProperties(
                        route, limit, List.of(rule("a", null, null), rule("a", "/x", null)), "X-Plan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }
}
