package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.application.port.ListRules;
import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ListRulesUseCase implements ListRules {

    private final RuleRepository rules;

    @Override
    public List<Rule> execute() {
        List<Rule> all = rules.findAll();
        log.debug("Listed {} rules", all.size());
        return all;
    }
}
