package io.github.jacksonvieira.ratelimiter.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Provider side of the contract with the gateway: {@code GET /api/rules} must return every field of
 * {@code contracts/rules-list.json}. The gateway's {@code AdminApiContractTest} reads the same file, so a
 * change that breaks one side fails the build.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RulesContractTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcClient jdbc;

    @Test
    void listReturnsEveryFieldOfTheContract() throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode contract = mapper.readTree(Files.readString(Path.of("..", "contracts", "rules-list.json")));
        jdbc.sql("delete from rate_limit_rule").update();
        RestClient client = RestClient.create("http://localhost:" + port);

        for (JsonNode rule : contract) {
            ObjectNode request = ((ObjectNode) rule.deepCopy());
            request.remove("position");
            client.post().uri("/api/rules").contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(request)).retrieve().toBodilessEntity();
        }

        JsonNode actual = mapper.readTree(client.get().uri("/api/rules").retrieve().body(String.class));

        assertThat(actual).hasSameSizeAs(contract);
        for (int i = 0; i < contract.size(); i++) {
            for (var field : contract.get(i).properties()) {
                assertThat(actual.get(i).get(field.getKey()))
                        .as("rule %d, field '%s'", i, field.getKey())
                        .isEqualTo(field.getValue());
            }
        }
    }
}
