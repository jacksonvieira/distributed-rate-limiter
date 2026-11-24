package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import io.github.jacksonvieira.ratelimiter.admin.domain.Algorithm;
import io.github.jacksonvieira.ratelimiter.admin.domain.KeyStrategy;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;

/**
 * Request body for creating or replacing a rule. {@code refillPeriod} is an ISO-8601 duration such as
 * {@code PT1S} or {@code PT1M}.
 */
record RuleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) @Pattern(regexp = "/.*", message = "must start with '/'") String pathPrefix,
        @Size(max = 100) String plan,
        @Positive Long capacity,
        @Positive Long refillTokens,
        String refillPeriod,
        Algorithm algorithm,
        KeyStrategy key,
        @Size(max = 100) String keyHeader) {

    RuleDefinition toDefinition() {
        return new RuleDefinition(
                name, pathPrefix, plan, capacity, refillTokens, parsePeriod(), algorithm, key, keyHeader);
    }

    private Duration parsePeriod() {
        if (refillPeriod == null) {
            return null;
        }
        try {
            return Duration.parse(refillPeriod);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("refillPeriod must be an ISO-8601 duration such as PT1S");
        }
    }
}
