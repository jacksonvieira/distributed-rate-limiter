package io.github.jacksonvieira.ratelimiter.gateway;

import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The plan comes from the API key, so a client cannot claim a bigger quota by sending its own plan header.
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
                "gateway.plan-by-api-key.paid-key=premium"
        })
class PlanByApiKeyIntegrationTest {

    private static final StubBackend BACKEND = startBackend();

    private static StubBackend startBackend() {
        try {
            return new StubBackend();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

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

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void aKeyMappedToAPlanGetsThatPlansLimit() {
        client.get().uri("/api/ping").header("X-API-Key", "paid-key").exchange()
                .expectHeader().valueEquals("X-RateLimit-Limit", "50");
    }

    @Test
    void thePlanHeaderIsIgnored() {
        client.get().uri("/api/ping").header("X-API-Key", "free-key").header("X-Plan", "premium").exchange()
                .expectHeader().valueEquals("X-RateLimit-Limit", "5");
    }
}
