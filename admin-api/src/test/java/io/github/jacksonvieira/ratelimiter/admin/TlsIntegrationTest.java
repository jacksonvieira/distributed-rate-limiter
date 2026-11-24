package io.github.jacksonvieira.ratelimiter.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/** Starts the admin API with {@code server.ssl.*} set and checks that it answers over HTTPS. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TlsIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static final String PASSWORD = "changeit";
    static Path keystore;

    static {
        POSTGRES.start();
        keystore = generateKeystore();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("server.ssl.key-store", () -> keystore.toUri().toString());
        registry.add("server.ssl.key-store-password", () -> PASSWORD);
        registry.add("server.ssl.key-store-type", () -> "PKCS12");
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void answersOverHttps() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            store.load(in, PASSWORD.toCharArray());
        }
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, trust.getTrustManagers(), null);

        RestClient client = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().sslContext(ssl).build()))
                .baseUrl("https://localhost:" + port)
                .build();

        assertThat(client.get().uri("/actuator/health").retrieve().body(String.class)).contains("UP");
    }

    private static Path generateKeystore() {
        try {
            Path file = Files.createTempDirectory("tls-test").resolve("keystore.p12");
            Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
            Process process = new ProcessBuilder(
                    keytool.toString(), "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
                    "-storetype", "PKCS12", "-keystore", file.toString(), "-storepass", PASSWORD,
                    "-dname", "CN=localhost", "-ext", "san=dns:localhost", "-validity", "2")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("keytool failed");
            }
            return file;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
