package io.github.jacksonvieira.ratelimiter.admin.application.port;

import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;

/** Inbound port: removes a rule, failing when it does not exist. */
public interface DeleteRule {

    /**
     * @throws RuleNotFoundException if no rule has the given id
     */
    void execute(long id);
}
