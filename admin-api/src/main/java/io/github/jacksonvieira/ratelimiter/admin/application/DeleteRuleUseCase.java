package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.application.port.DeleteRule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DeleteRuleUseCase implements DeleteRule {

    private final RuleRepository rules;

    @Override
    public void execute(long id) {
        if (!rules.deleteById(id)) {
            log.warn("Rule {} not found, nothing deleted", id);
            throw new RuleNotFoundException(id);
        }
        log.info("Deleted rule {}", id);
    }
}
