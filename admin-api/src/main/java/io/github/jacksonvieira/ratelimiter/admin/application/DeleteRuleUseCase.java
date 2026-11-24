package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.application.port.DeleteRule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteRuleUseCase implements DeleteRule {

    private final RuleRepository rules;

    @Override
    public void execute(long id) {
        if (!rules.deleteById(id)) {
            throw new RuleNotFoundException(id);
        }
    }
}
