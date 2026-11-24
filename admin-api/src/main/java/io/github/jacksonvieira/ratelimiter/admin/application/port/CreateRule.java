package io.github.jacksonvieira.ratelimiter.admin.application.port;

import io.github.jacksonvieira.ratelimiter.admin.domain.DuplicateRuleNameException;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;

/** Inbound port: appends a new rule after the existing ones. */
public interface CreateRule {

    /**
     * @return the stored rule, with its generated id and position
     * @throws DuplicateRuleNameException if the name is already used
     */
    Rule execute(RuleDefinition definition);
}
