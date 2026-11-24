package io.github.jacksonvieira.ratelimiter.admin.adapter.in.web;

import io.github.jacksonvieira.ratelimiter.admin.domain.Algorithm;
import io.github.jacksonvieira.ratelimiter.admin.domain.KeyStrategy;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;

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

    static RuleResponse from(Rule rule) {
        RuleDefinition d = rule.definition();
        return new RuleResponse(
                rule.id(),
                rule.position(),
                d.name(),
                d.pathPrefix(),
                d.plan(),
                d.capacity(),
                d.refillTokens(),
                d.refillPeriod() == null ? null : d.refillPeriod().toString(),
                d.algorithm(),
                d.key(),
                d.keyHeader());
    }
}
