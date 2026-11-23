package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.github.jacksonvieira.ratelimiter.redis.RedisTokenBucketRateLimiter;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

/** Runs the gateway with {@code gateway.rate-limit.store=redis} against a real Redis. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gateway.route.path-prefix=/api",
                "gateway.rate-limit.capacity=3",
                "gateway.rate-limit.refill-tokens=1",
                "gateway.rate-limit.refill-period=1h",
                "gateway.rate-limit.key=header",
                "gateway.rate-limit.store=redis"
        })
class RedisStoreIntegrationTest {

    private static final StubBackend BACKEND = startBackend();
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("gateway.route.uri", BACKEND::uri);
        registry.add("gateway.rate-limit.redis.uri",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stopInfrastructure() {
        BACKEND.close();
        REDIS.stop();
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    RateLimiter rateLimiter;

    @Test
    void usesTheRedisLimiter() {
        assertThat(rateLimiter).isInstanceOf(RedisTokenBucketRateLimiter.class);
    }

    @Test
    void rejectsWith429OnceTheSharedBucketIsEmpty() {
        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
        String apiKey = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            client.get().uri("/api/ping").header("X-API-Key", apiKey).exchange().expectStatus().isOk();
        }

        client.get().uri("/api/ping").header("X-API-Key", apiKey)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0");
    }

    private static StubBackend startBackend() {
        try {
            return new StubBackend();
        } catch (IOException e) {
            throw new IllegalStateException("could not start the stub backend", e);
        }
    }
}
