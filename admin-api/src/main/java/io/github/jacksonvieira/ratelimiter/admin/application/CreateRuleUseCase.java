package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.application.port.CreateRule;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CreateRuleUseCase implements CreateRule {

    private final RuleRepository rules;

    @Override
    public Rule execute(RuleDefinition definition) {
        Rule created = rules.create(definition);
        log.info("Created rule {} '{}' at position {}", created.id(), definition.name(), created.position());
        return created;
    }
}
