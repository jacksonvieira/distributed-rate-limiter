package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    static KeyResolver keyResolverFor(GatewayProperties.RateLimit rateLimit) {
        return switch (rateLimit.key()) {
            case IP -> KeyResolver.clientIp();
            case HEADER -> KeyResolver.header(rateLimit.keyHeader());
        };
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.admin-api.enabled", havingValue = "false", matchIfMissing = true)
    RateLimitPolicy rateLimitPolicy(
            GatewayProperties properties, RateLimiter rateLimiter, KeyResolver keyResolver, RateLimiterFactory factory) {
        return new RulePolicyBuilder(properties, factory, RateLimitPolicy.single(rateLimiter, keyResolver))
                .build(properties.rules());
    }

    @Bean
    TraceIdHeaderFilter traceIdHeaderFilter(io.micrometer.tracing.Tracer tracer) {
        return new TraceIdHeaderFilter(tracer);
    }

    @Bean
    RateLimitFilter rateLimitFilter(
            GatewayProperties properties, RateLimitPolicy policy, RateLimitObserver observer) {
        return new RateLimitFilter(properties.route(), policy, properties.rateLimit().onError(), observer);
    }

    @Bean
    ProxyHandler proxyHandler(GatewayProperties properties, WebClient.Builder webClientBuilder) {
        GatewayProperties.Route route = properties.route();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(route.connectTimeout().toMillis()))
                .responseTimeout(route.responseTimeout());
        WebClient webClient = webClientBuilder
                .clone()
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
