package io.github.jacksonvieira.ratelimiter.gateway;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Default limit is 5; {@code /api/login} is limited to 1 and the {@code premium} plan to 50.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gateway.route.path-prefix=/api",
                "gateway.rate-limit.capacity=5",
                "gateway.rate-limit.refill-tokens=1",
                "gateway.rate-limit.refill-period=1h",
                "gateway.rate-limit.key=header",
                "gateway.rules[0].name=premium",
                "gateway.rules[0].plan=premium",
                "gateway.rules[0].capacity=50",
                "gateway.rules[1].name=login",
                "gateway.rules[1].path-prefix=/api/login",
                "gateway.rules[1].capacity=1"
        })
class RateLimitRulesIntegrationTest {

    private static final StubBackend BACKEND = startBackend();

    @DynamicPropertySource
    static void backendUri(DynamicPropertyRegistry registry) {
        registry.add("gateway.route.uri", BACKEND::uri);
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.close();
    }

    @Value("${local.server.port}")
    int port;

    WebTestClient client;
    String apiKey;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        apiKey = UUID.randomUUID().toString();
    }

    @Test
    void requestsMatchingNoRuleUseTheDefaultLimit() {
        client.get().uri("/api/ping").header("X-API-Key", apiKey).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "5");
    }

    @Test
    void pathRuleOverridesOnlyTheConfiguredSettings() {
        client.get().uri("/api/login").header("X-API-Key", apiKey).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "1");
        client.get().uri("/api/login").header("X-API-Key", apiKey).exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void rulesCountSeparatelyFromTheDefaultLimit() {
        client.get().uri("/api/login").header("X-API-Key", apiKey).exchange().expectStatus().isOk();
        client.get().uri("/api/ping").header("X-API-Key", apiKey).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Remaining", "4");
    }

    @Test
    void planRuleAppliesWhenThePlanHeaderMatches() {
        client.get().uri("/api/ping").header("X-API-Key", apiKey).header("X-Plan", "premium").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "50");
        client.get().uri("/api/ping").header("X-API-Key", apiKey).header("X-Plan", "free").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "5");
    }

    @Test
    void firstMatchingRuleWins() {
        client.get().uri("/api/login").header("X-API-Key", apiKey).header("X-Plan", "premium").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "50");
    }

    private static StubBackend startBackend() {
        try {
            return new StubBackend();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the stub backend", e);
        }
    }
}
