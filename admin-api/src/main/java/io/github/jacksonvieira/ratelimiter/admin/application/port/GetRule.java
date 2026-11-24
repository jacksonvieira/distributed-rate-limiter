package io.github.jacksonvieira.ratelimiter.admin.application.port;

import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;

/** Inbound port: fetches one rule, failing when it does not exist. */
public interface GetRule {

    /**
     * @throws RuleNotFoundException if no rule has the given id
     */
    Rule execute(long id);
}
