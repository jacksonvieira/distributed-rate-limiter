package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Hot reload: when {@code gateway.admin-api.enabled} is on, rules come from the admin API and are re-read
 * periodically. The rules in {@code gateway.rules} apply until the first successful fetch.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "gateway.admin-api.enabled", havingValue = "true")
class DynamicRulesConfiguration implements SchedulingConfigurer {

    private final AdminApiProperties adminApi;
    private final RuleRefresher refresher;

    DynamicRulesConfiguration(AdminApiProperties adminApi, RuleRefresher refresher) {
        this.adminApi = adminApi;
        this.refresher = refresher;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(refresher::refresh, adminApi.refreshInterval());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "gateway.admin-api.enabled", havingValue = "true")
    static class Beans {

        @Bean
        RuleSource ruleSource(AdminApiProperties adminApi) {
            WebClient client = WebClient.builder().baseUrl(adminApi.url().toString()).build();
            return new AdminApiRuleSource(client, adminApi.timeout());
        }

        @Bean
        ReloadableRateLimitPolicy reloadableRateLimitPolicy(
                GatewayProperties properties,
                RateLimiter rateLimiter,
                KeyResolver keyResolver,
                RateLimiterFactory factory) {
            RulePolicyBuilder builder = new RulePolicyBuilder(
                    properties, factory, RateLimitPolicy.single(rateLimiter, keyResolver));
            return new ReloadableRateLimitPolicy(builder, properties.rules());
        }

        @Bean
        RuleRefresher ruleRefresher(RuleSource source, ReloadableRateLimitPolicy policy) {
            return new RuleRefresher(source, policy);
        }
    }
}
