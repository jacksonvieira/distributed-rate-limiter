package io.github.jacksonvieira.ratelimiter.admin.application.port;

import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;

/** Inbound port: appends a new rule after the existing ones. */
public interface CreateRule {

    Rule execute(RuleDefinition definition);
}
