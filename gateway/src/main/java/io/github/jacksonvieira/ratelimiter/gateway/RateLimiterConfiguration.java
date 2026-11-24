package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.FixedWindowConfig;
import io.github.jacksonvieira.ratelimiter.core.InMemoryFixedWindowRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.InMemorySlidingWindowLogRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.InMemoryTokenBucketRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.SlidingWindowConfig;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.github.jacksonvieira.ratelimiter.redis.RedisTokenBucketRateLimiter;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the {@link RateLimiter} implementation from {@code gateway.rate-limit.store} and {@code gateway.rate-limit.algorithm}, so the rest
 * of the gateway only ever depends on the {@code RateLimiter} port.
 */
@Configuration(proxyBeanMethods = false)
class RateLimiterConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gateway.rate-limit.store", havingValue = "memory", matchIfMissing = true)
    RateLimiter inMemoryRateLimiter(GatewayProperties properties) {
        GatewayProperties.RateLimit rateLimit = properties.rateLimit();
        return switch (rateLimit.algorithm()) {
            case TOKEN_BUCKET -> new InMemoryTokenBucketRateLimiter(rateLimit.toTokenBucketConfig());
            case FIXED_WINDOW -> new InMemoryFixedWindowRateLimiter(
                    new FixedWindowConfig(rateLimit.capacity(), rateLimit.refillPeriod()));
            case SLIDING_WINDOW_LOG -> new InMemorySlidingWindowLogRateLimiter(
                    new SlidingWindowConfig(rateLimit.capacity(), rateLimit.refillPeriod()));
        };
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
            GatewayProperties.RateLimit rateLimit = properties.rateLimit();
            if (rateLimit.algorithm() != GatewayProperties.Algorithm.TOKEN_BUCKET) {
                throw new IllegalStateException(
                        "gateway.rate-limit.algorithm=" + rateLimit.algorithm() + " is not available with store=redis yet");
            }
            return new RedisTokenBucketRateLimiter(connection, rateLimit.toTokenBucketConfig());
        }
    }
}
