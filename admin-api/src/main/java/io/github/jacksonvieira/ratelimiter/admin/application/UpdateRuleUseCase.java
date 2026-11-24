package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.application.port.UpdateRule;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class UpdateRuleUseCase implements UpdateRule {

    private final RuleRepository rules;

    @Override
    public Rule execute(long id, RuleDefinition definition) {
        Rule updated = rules.update(id, definition);
        log.info("Updated rule {} '{}'", id, definition.name());
        return updated;
    }
}
