package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.InMemoryTokenBucketRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.github.jacksonvieira.ratelimiter.redis.RedisTokenBucketRateLimiter;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the {@link RateLimiter} implementation from {@code gateway.rate-limit.store}, so the rest
 * of the gateway only ever depends on the {@code RateLimiter} port.
 */
@Configuration(proxyBeanMethods = false)
class RateLimiterConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gateway.rate-limit.store", havingValue = "memory", matchIfMissing = true)
    RateLimiter inMemoryRateLimiter(GatewayProperties properties) {
        return new InMemoryTokenBucketRateLimiter(properties.rateLimit().toTokenBucketConfig());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "gateway.rate-limit.store", havingValue = "redis")
    static class RedisRateLimiterConfiguration {

        @Bean(destroyMethod = "shutdown")
        RedisClient redisClient(GatewayProperties properties) {
            return RedisClient.create(properties.rateLimit().redis().uri());
        }

        @Bean(destroyMethod = "close")
        StatefulRedisConnection<String, String> redisConnection(RedisClient redisClient) {
            return redisClient.connect();
        }

        @Bean
        RateLimiter redisRateLimiter(GatewayProperties properties, StatefulRedisConnection<String, String> connection) {
            return new RedisTokenBucketRateLimiter(connection, properties.rateLimit().toTokenBucketConfig());
        }
    }
}
