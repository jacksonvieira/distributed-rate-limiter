package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GatewayPropertiesTest {

    private static GatewayProperties.Route route(String prefix, boolean strip) {
        return new GatewayProperties.Route(
                prefix, URI.create("http://backend"), strip, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void matchesThePrefixOnlyOnPathSegmentBoundaries() {
        GatewayProperties.Route route = route("/api", true);

        assertThat(route.matches("/api")).isTrue();
        assertThat(route.matches("/api/users")).isTrue();
        assertThat(route.matches("/apix")).isFalse();
        assertThat(route.matches("/other/api")).isFalse();
    }

    @Test
    void trailingSlashInThePrefixIsIgnored() {
        GatewayProperties.Route route = route("/api/", true);

        assertThat(route.pathPrefix()).isEqualTo("/api");
        assertThat(route.matches("/api/users")).isTrue();
    }

    @Test
    void stripsThePrefixWhenConfigured() {
        assertThat(route("/api", true).backendPath("/api/users")).isEqualTo("/users");
        assertThat(route("/api", true).backendPath("/api")).isEqualTo("/");
        assertThat(route("/api", false).backendPath("/api/users")).isEqualTo("/api/users");
    }

    @Test
    void rootPrefixMatchesEverythingAndStripsNothing() {
        GatewayProperties.Route route = route("/", true);

        assertThat(route.matches("/anything")).isTrue();
        assertThat(route.backendPath("/anything")).isEqualTo("/anything");
    }

    @Test
    void rejectsPrefixWithoutLeadingSlash() {
        assertThatIllegalArgumentException().isThrownBy(() -> route("api", true));
    }
}
