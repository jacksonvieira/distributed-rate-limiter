package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.application.port.UpdateRule;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UpdateRuleUseCase implements UpdateRule {

    private final RuleRepository rules;

    @Override
    public Rule execute(long id, RuleDefinition definition) {
        return rules.update(id, definition);
    }
}
