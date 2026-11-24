package io.github.jacksonvieira.ratelimiter.admin.application.port;

import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;

/** Inbound port: replaces a rule's definition, keeping its position. */
public interface UpdateRule {

    Rule execute(long id, RuleDefinition definition);
}
