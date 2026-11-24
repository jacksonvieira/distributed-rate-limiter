package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.netty.channel.ChannelOption;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.netty.http.client.HttpClient;

@Configuration(proxyBeanMethods = false)
class GatewayConfiguration {

    @Bean
    KeyResolver keyResolver(GatewayProperties properties) {
        return keyResolverFor(properties.rateLimit());
    }

    private static KeyResolver keyResolverFor(GatewayProperties.RateLimit rateLimit) {
        return switch (rateLimit.key()) {
            case IP -> KeyResolver.clientIp();
            case HEADER -> KeyResolver.header(rateLimit.keyHeader());
        };
    }

    @Bean
    RateLimitPolicy rateLimitPolicy(
            GatewayProperties properties, RateLimiter rateLimiter, KeyResolver keyResolver, RateLimiterFactory factory) {
        RateLimitPolicy defaultPolicy = RateLimitPolicy.single(rateLimiter, keyResolver);
        if (properties.rules().isEmpty()) {
            return defaultPolicy;
        }
        List<RuleBasedRateLimitPolicy.CompiledRule> compiled = properties.rules().stream()
                .map(rule -> {
                    GatewayProperties.RateLimit effective = rule.applyTo(properties.rateLimit());
                    return new RuleBasedRateLimitPolicy.CompiledRule(
                            rule, factory.create(effective, rule.name()), keyResolverFor(effective));
                })
                .toList();
        return new RuleBasedRateLimitPolicy(compiled, defaultPolicy, properties.planHeader());
    }

    @Bean
    RateLimitFilter rateLimitFilter(GatewayProperties properties, RateLimitPolicy policy) {
        return new RateLimitFilter(properties.route(), policy, properties.rateLimit().onError());
    }

    @Bean
    ProxyHandler proxyHandler(GatewayProperties properties) {
        GatewayProperties.Route route = properties.route();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(route.connectTimeout().toMillis()))
                .responseTimeout(route.responseTimeout());
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        return new ProxyHandler(route, webClient);
    }

    @Bean
    RouterFunction<ServerResponse> gatewayRoute(GatewayProperties properties, ProxyHandler proxyHandler) {
        GatewayProperties.Route route = properties.route();
        return RouterFunctions.route(request -> route.matches(request.path()), proxyHandler::forward);
    }
}
