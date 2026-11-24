package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import io.github.jacksonvieira.ratelimiter.admin.domain.Algorithm;
import io.github.jacksonvieira.ratelimiter.admin.domain.KeyStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or replacing a rule. {@code refillPeriod} is an ISO-8601 duration such as
 * {@code PT1S} or {@code PT1M}.
 */
@Schema(name = "RuleRequest")
record RuleRequest(
        @Schema(example = "login") @NotBlank @Size(max = 100) String name,
        @Size(max = 255) @Pattern(regexp = "/.*", message = "must start with '/'") String pathPrefix,
        @Size(max = 100) String plan,
        @Positive Long capacity,
        @Positive Long refillTokens,
        @Schema(example = "PT1M", description = "ISO-8601 duration") String refillPeriod,
        Algorithm algorithm,
        KeyStrategy key,
        @Size(max = 100) String keyHeader) {
}
