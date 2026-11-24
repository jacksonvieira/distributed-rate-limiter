package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.application.port.ManageRules;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RuleService implements ManageRules {

    private final RuleRepository rules;

    @Override
    public List<Rule> list() {
        return rules.findAll();
    }

    @Override
    public Rule get(long id) {
        return rules.findById(id).orElseThrow(() -> new RuleNotFoundException(id));
    }

    @Override
    public Rule create(RuleDefinition definition) {
        return rules.create(definition);
    }

    @Override
    public Rule update(long id, RuleDefinition definition) {
        return rules.update(id, definition);
    }

    @Override
    public void delete(long id) {
        if (!rules.deleteById(id)) {
            throw new RuleNotFoundException(id);
        }
    }
}
