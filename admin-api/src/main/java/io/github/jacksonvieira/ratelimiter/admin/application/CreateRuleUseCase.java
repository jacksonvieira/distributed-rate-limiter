package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.application.port.CreateRule;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateRuleUseCase implements CreateRule {

    private final RuleRepository rules;

    @Override
    public Rule execute(RuleDefinition definition) {
        return rules.create(definition);
    }
}
