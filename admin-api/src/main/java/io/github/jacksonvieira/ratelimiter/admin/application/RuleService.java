package io.github.jacksonvieira.ratelimiter.admin.application;

import io.github.jacksonvieira.ratelimiter.admin.domain.Rule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleDefinition;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleNotFoundException;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository rules;

    public List<Rule> list() {
        return rules.findAll();
    }

    public Rule get(long id) {
        return rules.findById(id).orElseThrow(() -> new RuleNotFoundException(id));
    }

    public Rule create(RuleDefinition definition) {
        return rules.create(definition);
    }

    public Rule update(long id, RuleDefinition definition) {
        return rules.update(id, definition);
    }

    public void delete(long id) {
        if (!rules.deleteById(id)) {
            throw new RuleNotFoundException(id);
        }
    }
}
