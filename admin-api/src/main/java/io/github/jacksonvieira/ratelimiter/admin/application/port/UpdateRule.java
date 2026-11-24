package io.github.jacksonvieira.ratelimiter.admin.application.port;

import io.github.jacksonvieira.ratelimiter.admin.domain.DuplicateRuleNameException;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;

/** Inbound port: replaces a rule's definition, keeping its position. */
public interface UpdateRule {

    /**
     * @return the updated rule
     * @throws RuleNotFoundException      if no rule has the given id
     * @throws DuplicateRuleNameException if another rule already uses the name
     */
    Rule execute(long id, RuleDefinition definition);
}
