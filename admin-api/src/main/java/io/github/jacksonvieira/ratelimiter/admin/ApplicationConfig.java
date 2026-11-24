package io.github.jacksonvieira.ratelimiter.admin;

import io.github.jacksonvieira.ratelimiter.admin.application.RuleService;
import io.github.jacksonvieira.ratelimiter.admin.application.port.ManageRules;
import io.github.jacksonvieira.ratelimiter.admin.domain.RuleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root: wires the framework-free application layer to its adapters. */
@Configuration
class ApplicationConfig {

    @Bean
    ManageRules manageRules(RuleRepository rules) {
        return new RuleService(rules);
    }
}
