package io.github.jacksonvieira.ratelimiter.admin;

import io.github.jacksonvieira.ratelimiter.admin.application.CreateRuleUseCase;
import io.github.jacksonvieira.ratelimiter.admin.application.port.CreateRule;
import io.github.jacksonvieira.ratelimiter.admin.application.DeleteRuleUseCase;
import io.github.jacksonvieira.ratelimiter.admin.application.port.DeleteRule;
import io.github.jacksonvieira.ratelimiter.admin.application.GetRuleUseCase;
import io.github.jacksonvieira.ratelimiter.admin.application.port.GetRule;
import io.github.jacksonvieira.ratelimiter.admin.application.ListRulesUseCase;
import io.github.jacksonvieira.ratelimiter.admin.application.port.ListRules;
import io.github.jacksonvieira.ratelimiter.admin.application.UpdateRuleUseCase;
import io.github.jacksonvieira.ratelimiter.admin.application.port.UpdateRule;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root: wires the framework-free use cases to their adapters. */
@Configuration
class ApplicationConfig {

    @Bean
    ListRules listRules(RuleRepository rules) {
        return new ListRulesUseCase(rules);
    }

    @Bean
    GetRule getRule(RuleRepository rules) {
        return new GetRuleUseCase(rules);
    }

    @Bean
    CreateRule createRule(RuleRepository rules) {
        return new CreateRuleUseCase(rules);
    }

    @Bean
    UpdateRule updateRule(RuleRepository rules) {
        return new UpdateRuleUseCase(rules);
    }

    @Bean
    DeleteRule deleteRule(RuleRepository rules) {
        return new DeleteRuleUseCase(rules);
    }
}
