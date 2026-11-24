package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Runs the gateway on a real port in front of a stub backend.
 *
 * <p>Buckets hold 3 tokens and refill one per hour, so they never refill during a test.
 * Each test uses its own API key, and therefore its own bucket.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gateway.route.path-prefix=/api",
                "gateway.rate-limit.capacity=3",
                "gateway.rate-limit.refill-tokens=1",
                "gateway.rate-limit.refill-period=1h",
                "gateway.rate-limit.key=header",
                "gateway.rate-limit.key-header=X-API-Key"
        })
class GatewayIntegrationTest {

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
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
        apiKey = UUID.randomUUID().toString();
    }

    @Test
    void forwardsRequestWithoutThePrefixAndKeepsTheQuery() {
        client.get().uri("/api/users/42?expand=orders&page=2")
                .header("X-API-Key", apiKey)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Backend", "stub")
                .expectHeader().valueEquals("X-RateLimit-Limit", "3")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "2")
                .expectBody(String.class).value(body -> assertThat(body)
                        .startsWith("GET /users/42?expand=orders&page=2")
                        .contains("x-forwarded-for: 127.0.0.1"));
    }

    @Test
    void continuesTheCallersTraceTowardsTheBackend() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        client.get().uri("/api/users/42")
                .header("X-API-Key", apiKey)
                .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("traceparent: 00-" + traceId + "-")
                        .doesNotContain("-00f067aa0ba902b7-"));
    }

    @Test
    void returnsTheTraceIdToTheClient() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        client.get().uri("/api/users/42")
                .header("X-API-Key", apiKey)
                .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01")
                .exchange()
                .expectHeader().valueEquals("X-Trace-Id", traceId);
        client.get().uri("/api/users/42")
                .header("X-API-Key", apiKey)
                .exchange()
                .expectHeader().value("X-Trace-Id", id -> assertThat(id).hasSize(32));
    }

    @Test
    void forwardsTheRequestBodyAndBackendStatus() {
        client.post().uri("/api/orders")
                .header("X-API-Key", apiKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"item\":\"book\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class).value(body -> assertThat(body)
                        .startsWith("POST /orders")
                        .endsWith("body: {\"item\":\"book\"}"));
    }

    @Test
    void passesBackendErrorsThrough() {
        client.get().uri("/api/status/503")
                .header("X-API-Key", apiKey)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().valueEquals("X-Backend", "stub");
    }

    @Test
    void rejectsWith429OnceTheBucketIsEmptyWithoutCallingTheBackend() {
        for (int i = 0; i < 3; i++) {
            client.get().uri("/api/ping").header("X-API-Key", apiKey).exchange().expectStatus().isOk();
        }
        int backendRequestsBefore = BACKEND.requests();

        client.get().uri("/api/ping")
                .header("X-API-Key", apiKey)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().valueEquals("X-RateLimit-Limit", "3")
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
                // One token per hour: the next one arrives in 3600 seconds.
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "3600");

        assertThat(BACKEND.requests()).isEqualTo(backendRequestsBefore);
    }

    @Test
    void eachApiKeyHasItsOwnBucket() {
        for (int i = 0; i < 3; i++) {
            client.get().uri("/api/ping").header("X-API-Key", apiKey).exchange().expectStatus().isOk();
        }
        client.get().uri("/api/ping").header("X-API-Key", apiKey)
                .exchange().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        client.get().uri("/api/ping").header("X-API-Key", UUID.randomUUID().toString())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Remaining", "2");
    }

    @Test
    void pathsOutsideTheRouteAreNotForwardedNorLimited() {
        client.get().uri("/apix/ping")
                .header("X-API-Key", apiKey)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().doesNotExist("X-RateLimit-Limit");
    }

    private static StubBackend startBackend() {
        try {
            return new StubBackend();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the stub backend", e);
        }
    }
}
