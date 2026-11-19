package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class KeyResolverTest {

    private static final InetSocketAddress CLIENT = new InetSocketAddress("203.0.113.7", 51000);

    @Test
    void clientIpUsesThePeerAddressAndIgnoresForwardedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api")
                .remoteAddress(CLIENT)
                .header("X-Forwarded-For", "1.2.3.4")
                .build();

        assertThat(KeyResolver.clientIp().resolve(request)).isEqualTo("ip:203.0.113.7");
    }

    @Test
    void headerUsesTheHeaderValue() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api")
                .remoteAddress(CLIENT)
                .header("X-API-Key", " abc ")
                .build();

        assertThat(KeyResolver.header("X-API-Key").resolve(request)).isEqualTo("key:abc");
    }

    @Test
    void headerFallsBackToTheClientIpWhenMissingOrBlank() {
        MockServerHttpRequest missing = MockServerHttpRequest.get("/api").remoteAddress(CLIENT).build();
        MockServerHttpRequest blank = MockServerHttpRequest.get("/api")
                .remoteAddress(CLIENT).header("X-API-Key", "  ").build();

        assertThat(KeyResolver.header("X-API-Key").resolve(missing)).isEqualTo("ip:203.0.113.7");
        assertThat(KeyResolver.header("X-API-Key").resolve(blank)).isEqualTo("ip:203.0.113.7");
    }
}
