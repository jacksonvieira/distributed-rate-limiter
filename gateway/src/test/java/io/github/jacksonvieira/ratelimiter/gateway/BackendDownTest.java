package io.github.jacksonvieira.ratelimiter.gateway;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendDownTest {

    @DynamicPropertySource
    static void unreachableBackend(DynamicPropertyRegistry registry) throws IOException {
        // Grab a free port and release it, so nothing is listening there.
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        registry.add("gateway.route.uri", () -> "http://127.0.0.1:" + port);
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void answers502WhenTheBackendIsUnreachable() {
        WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .get().uri("/api/ping")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectHeader().exists("X-RateLimit-Limit");
    }
}
