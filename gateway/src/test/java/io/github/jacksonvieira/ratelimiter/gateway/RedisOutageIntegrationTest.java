package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.toxiproxy.ToxiproxyContainer;

/**
 * Cuts the connection between the gateway and Redis (through Toxiproxy) and checks that the limit keeps being
 * enforced by the in-memory fallback, then goes back to Redis once the connection is restored.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gateway.route.path-prefix=/api",
                "gateway.rate-limit.capacity=3",
                "gateway.rate-limit.refill-tokens=1",
                "gateway.rate-limit.refill-period=1h",
                "gateway.rate-limit.key=header",
                "gateway.rate-limit.store=redis",
                "gateway.rate-limit.redis.circuit-breaker.enabled=true",
                "gateway.rate-limit.redis.circuit-breaker.failure-threshold=1",
                "gateway.rate-limit.redis.circuit-breaker.open-duration=1s"
        })
class RedisOutageIntegrationTest {

    private static final int PROXY_PORT = 8666;
    private static final StubBackend BACKEND = startBackend();
    private static final Network NETWORK = Network.newNetwork();
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withNetwork(NETWORK)
            .withNetworkAliases("redis");
    private static final ToxiproxyContainer TOXIPROXY =
            new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0").withNetwork(NETWORK);
    private static final Proxy PROXY;

    static {
        REDIS.start();
        TOXIPROXY.start();
        try {
            PROXY = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort())
                    .createProxy("redis", "0.0.0.0:" + PROXY_PORT, "redis:6379");
        } catch (IOException e) {
            throw new IllegalStateException("could not create the Toxiproxy proxy", e);
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("gateway.route.uri", BACKEND::uri);
        registry.add("gateway.rate-limit.redis.uri",
                () -> "redis://" + TOXIPROXY.getHost() + ":" + TOXIPROXY.getMappedPort(PROXY_PORT));
    }

    @AfterAll
    static void stopInfrastructure() {
        BACKEND.close();
        TOXIPROXY.stop();
        REDIS.stop();
        NETWORK.close();
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void keepsLimitingWhileRedisIsDownAndReturnsToRedisAfterwards() throws Exception {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();

        PROXY.disable();
        String duringOutage = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++) {
            assertThat(status(client, duringOutage)).isEqualTo(HttpStatus.OK);
        }
        assertThat(status(client, duringOutage)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        PROXY.enable();
        String afterRecovery = UUID.randomUUID().toString();
        try (RedisClient redisClient = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
                StatefulRedisConnection<String, String> redis = redisClient.connect()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (redis.sync().keys("rl:tb:*").isEmpty() && System.nanoTime() < deadline) {
                status(client, afterRecovery);
                Thread.sleep(500);
            }
            assertThat(redis.sync().keys("rl:tb:*")).as("the gateway is using Redis again").isNotEmpty();
        }
    }

    private static HttpStatus status(WebTestClient client, String apiKey) {
        return HttpStatus.valueOf(client.get().uri("/api/ping").header("X-API-Key", apiKey)
                .exchange().returnResult(Void.class).getStatus().value());
    }

    private static StubBackend startBackend() {
        try {
            return new StubBackend();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the stub backend", e);
        }
    }
}
