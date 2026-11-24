package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import io.github.jacksonvieira.ratelimiter.admin.domain.Algorithm;
import io.github.jacksonvieira.ratelimiter.admin.domain.KeyStrategy;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RuleResponse")
record RuleResponse(
        long id,
        int position,
        String name,
        String pathPrefix,
        String plan,
        Long capacity,
        Long refillTokens,
        String refillPeriod,
        Algorithm algorithm,
        KeyStrategy key,
        String keyHeader) {
}
