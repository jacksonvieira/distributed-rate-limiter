package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The gateway starts with a generous default limit; rules served by a fake admin API tighten it at runtime,
 * and removing them loosens it again, all without a restart.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gateway.route.path-prefix=/api",
                "gateway.rate-limit.capacity=100",
                "gateway.rate-limit.refill-tokens=1",
                "gateway.rate-limit.refill-period=1h",
                "gateway.rate-limit.key=header",
                "gateway.admin-api.enabled=true",
                "gateway.admin-api.refresh-interval=100ms"
        })
class DynamicRulesIntegrationTest {

    private static final StubBackend BACKEND = startBackend();
    private static volatile String adminBody = "[]";
    private static final HttpServer ADMIN = startAdmin();

    @DynamicPropertySource
    static void uris(DynamicPropertyRegistry registry) {
        registry.add("gateway.route.uri", BACKEND::uri);
        registry.add("gateway.admin-api.url", () -> "http://127.0.0.1:" + ADMIN.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        BACKEND.close();
        ADMIN.stop(0);
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void appliesAndRemovesRulesWithoutARestart() {
        WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        String apiKey = UUID.randomUUID().toString();

        assertThat(statusOf(client, apiKey)).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(client, apiKey)).isEqualTo(HttpStatus.OK);

        adminBody = """
                [{"id":1,"position":1,"name":"strict","pathPrefix":"/api","capacity":1,
                  "refillTokens":1,"refillPeriod":"PT1H","algorithm":"TOKEN_BUCKET","key":"HEADER"}]""";
        awaitUntil(() -> {
            String key = UUID.randomUUID().toString();
            statusOf(client, key);
            return statusOf(client, key) == HttpStatus.TOO_MANY_REQUESTS;
        });
        String fresh = UUID.randomUUID().toString();
        assertThat(statusOf(client, fresh)).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(client, fresh)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        adminBody = "[]";
        awaitUntil(() -> statusOf(client, fresh) == HttpStatus.OK);
    }

    @Test
    void keepsTheCurrentRulesWhenTheAdminApiIsMalformed() throws Exception {
        WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        String previous = adminBody;
        adminBody = "not json";
        Thread.sleep(400);

        assertThat(statusOf(client, UUID.randomUUID().toString())).isEqualTo(HttpStatus.OK);
        adminBody = previous;
    }

    private static HttpStatus statusOf(WebTestClient client, String apiKey) {
        return HttpStatus.valueOf(client.get().uri("/api/x").header("X-API-Key", apiKey).exchange()
                .returnResult(String.class).getStatus().value());
    }

    private static void awaitUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("condition not met within 10s");
    }

    private static StubBackend startBackend() {
        try {
            return new StubBackend();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startAdmin() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/rules", exchange -> {
                byte[] bytes = adminBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
