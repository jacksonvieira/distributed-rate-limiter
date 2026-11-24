package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.application.port.GetRule;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GetRuleUseCase implements GetRule {

    private final RuleRepository rules;

    @Override
    public Rule execute(long id) {
        return rules.findById(id).orElseThrow(() -> {
            log.warn("Rule {} not found", id);
            return new RuleNotFoundException(id);
        });
    }
}
