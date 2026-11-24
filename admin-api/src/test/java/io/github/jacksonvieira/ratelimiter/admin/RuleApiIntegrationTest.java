package io.github.jacksonvieira.ratelimiter.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/** Exercises the REST API end to end against a real PostgreSQL, with the Flyway migrations applied. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RuleApiIntegrationTest {

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

    RestClient client;

    @BeforeEach
    void setUp() {
        jdbc.sql("delete from rate_limit_rule").update();
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    private ResponseEntity<String> post(String json) {
        return client.post().uri("/api/rules").contentType(MediaType.APPLICATION_JSON).body(json).retrieve()
                .toEntity(String.class);
    }

    private static long idOf(ResponseEntity<String> response) {
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(response.getBody());
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    @Test
    void createsAndReadsARule() {
        ResponseEntity<String> created = post("""
                {"name":"login","pathPrefix":"/api/login/","capacity":5,"refillTokens":1,
                 "refillPeriod":"PT1M","algorithm":"FIXED_WINDOW","key":"HEADER"}""");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = idOf(created);
        assertThat(created.getHeaders().getLocation()).hasPath("/api/rules/" + id);

        ResponseEntity<String> fetched = client.get().uri("/api/rules/{id}", id).retrieve().toEntity(String.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody())
                .contains("\"name\":\"login\"")
                .contains("\"pathPrefix\":\"/api/login\"")
                .contains("\"refillPeriod\":\"PT1M\"")
                .contains("\"algorithm\":\"FIXED_WINDOW\"")
                .contains("\"key\":\"HEADER\"");
    }

    @Test
    void listsRulesInCreationOrder() {
        post("{\"name\":\"first\"}");
        post("{\"name\":\"second\"}");
        post("{\"name\":\"third\"}");

        String body = client.get().uri("/api/rules").retrieve().body(String.class);

        assertThat(body.indexOf("first")).isLessThan(body.indexOf("second"));
        assertThat(body.indexOf("second")).isLessThan(body.indexOf("third"));
    }

    @Test
    void updatesARuleKeepingItsPosition() {
        long id = idOf(post("{\"name\":\"a\",\"capacity\":1}"));
        post("{\"name\":\"b\"}");

        ResponseEntity<String> updated = client.put().uri("/api/rules/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"a2\",\"capacity\":9}")
                .retrieve().toEntity(String.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).contains("\"name\":\"a2\"").contains("\"capacity\":9").contains("\"position\":1");
    }

    @Test
    void deletesARule() {
        long id = idOf(post("{\"name\":\"gone\"}"));

        assertThat(client.delete().uri("/api/rules/{id}", id).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(client.get().uri("/api/rules/{id}", id).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(client.delete().uri("/api/rules/{id}", id).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsADuplicateName() {
        post("{\"name\":\"dup\"}");

        assertThat(post("{\"name\":\"dup\"}").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void publishesTheOpenApiDocument() {
        ResponseEntity<String> docs = client.get().uri("/v3/api-docs").retrieve().toEntity(String.class);

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody()).contains("/api/rules/{id}").contains("RuleRequest");
    }

    @Test
    void rejectsInvalidInput() {
        assertThat(post("{\"name\":\"\"}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("{\"name\":\"x\",\"pathPrefix\":\"login\"}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("{\"name\":\"x\",\"capacity\":0}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("{\"name\":\"x\",\"refillPeriod\":\"soon\"}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
