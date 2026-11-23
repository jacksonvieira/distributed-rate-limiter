package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.netty.channel.ChannelOption;
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
        GatewayProperties.RateLimit rateLimit = properties.rateLimit();
        return switch (rateLimit.key()) {
            case IP -> KeyResolver.clientIp();
            case HEADER -> KeyResolver.header(rateLimit.keyHeader());
        };
    }

    @Bean
    RateLimitFilter rateLimitFilter(GatewayProperties properties, RateLimiter rateLimiter, KeyResolver keyResolver) {
        return new RateLimitFilter(properties.route(), rateLimiter, keyResolver, properties.rateLimit().onError());
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
