package io.github.jacksonvieira.ratelimiter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Consumer side of the contract with the admin API: the gateway must understand the response described in
 * {@code contracts/rules-list.json}, which the admin API's {@code RulesContractTest} checks it produces.
 */
class AdminApiContractTest {

    @Test
    void readsTheRulesTheAdminApiReturns() throws IOException {
        String body = Files.readString(Path.of("..", "contracts", "rules-list.json"));
        WebClient client = WebClient.builder()
                .baseUrl("http://admin")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(body)
                        .build()))
                .build();

        List<GatewayProperties.Rule> rules = new AdminApiRuleSource(client, Duration.ofSeconds(5)).load();

        assertThat(rules).hasSize(2);
        GatewayProperties.Rule login = rules.get(0);
        assertThat(login.name()).isEqualTo("login");
        assertThat(login.pathPrefix()).isEqualTo("/api/login");
        assertThat(login.plan()).isEqualTo("premium");
        assertThat(login.capacity()).isEqualTo(5);
        assertThat(login.refillTokens()).isEqualTo(1);
        assertThat(login.refillPeriod()).isEqualTo(Duration.ofMinutes(1));
        assertThat(login.algorithm()).isEqualTo(GatewayProperties.Algorithm.FIXED_WINDOW);
        assertThat(login.key()).isEqualTo(GatewayProperties.KeyStrategy.HEADER);
        assertThat(login.keyHeader()).isEqualTo("X-Api-Key");

        GatewayProperties.Rule fallback = rules.get(1);
        assertThat(fallback.pathPrefix()).isNull();
        assertThat(fallback.plan()).isNull();
        assertThat(fallback.algorithm()).isEqualTo(GatewayProperties.Algorithm.TOKEN_BUCKET);
        assertThat(fallback.key()).isEqualTo(GatewayProperties.KeyStrategy.IP);
    }
}
