package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * Starts several complete gateway instances against one Redis and checks that together they
 * enforce a single limit, as a load balancer in front of them would experience it.
 */
class SharedLimitAcrossInstancesTest {

    private static final int INSTANCES = 3;
    private static final int CAPACITY = 6;

    private static StubBackend backend;
    private static GenericContainer<?> redis;
    private static final List<ConfigurableApplicationContext> CONTEXTS = new ArrayList<>();
    private static final List<WebTestClient> CLIENTS = new ArrayList<>();

    @BeforeAll
    static void startInstances() throws IOException {
        backend = new StubBackend();
        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();

        for (int i = 0; i < INSTANCES; i++) {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(GatewayApplication.class)
                    .web(WebApplicationType.REACTIVE)
                    // Command-line arguments outrank application.yml, which fixes the port at 8080.
                    .run(
                            "--server.port=0",
                            "--gateway.route.path-prefix=/api",
                            "--gateway.route.uri=" + backend.uri(),
                            "--gateway.rate-limit.capacity=" + CAPACITY,
                            "--gateway.rate-limit.refill-tokens=1",
                            "--gateway.rate-limit.refill-period=1h",
                            "--gateway.rate-limit.key=header",
                            "--gateway.rate-limit.store=redis",
                            "--gateway.rate-limit.redis.uri=redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
            CONTEXTS.add(context);
            CLIENTS.add(WebTestClient.bindToServer()
                    .baseUrl("http://localhost:" + context.getEnvironment().getProperty("local.server.port"))
                    .responseTimeout(Duration.ofSeconds(10))
                    .build());
        }
    }

    @AfterAll
    static void stopInstances() {
        CONTEXTS.forEach(ConfigurableApplicationContext::close);
        redis.stop();
        backend.close();
    }

    @Test
    void instancesEnforceOneSharedLimit() {
        String apiKey = UUID.randomUUID().toString();
        int allowed = 0;
        int rejected = 0;

        for (int i = 0; i < CAPACITY * 2; i++) {
            HttpStatus status = (HttpStatus) CLIENTS.get(i % INSTANCES).get().uri("/api/ping")
                    .header("X-API-Key", apiKey)
                    .exchange()
                    .returnResult(Void.class)
                    .getStatus();
            if (status == HttpStatus.OK) {
                allowed++;
            } else if (status == HttpStatus.TOO_MANY_REQUESTS) {
                rejected++;
            }
        }

        assertThat(allowed).isEqualTo(CAPACITY);
        assertThat(rejected).isEqualTo(CAPACITY);
    }
}
