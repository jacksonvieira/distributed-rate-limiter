package io.github.jacksonvieira.ratelimiter.admin.application.port;

import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import java.util.List;

/** Inbound port: the use cases driving adapters (REST today, anything else tomorrow) can invoke. */
public interface ManageRules {

    List<Rule> list();

    Rule get(long id);

    Rule create(RuleDefinition definition);

    Rule update(long id, RuleDefinition definition);

    void delete(long id);
}
