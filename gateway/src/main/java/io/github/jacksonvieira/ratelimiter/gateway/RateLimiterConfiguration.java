package io.github.jacksonvieira.ratelimiter.gateway;

import io.github.jacksonvieira.ratelimiter.core.CircuitBreakerRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.FixedWindowConfig;
import io.github.jacksonvieira.ratelimiter.core.InMemoryFixedWindowRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.InMemorySlidingWindowCounterRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.InMemorySlidingWindowLogRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.InMemoryTokenBucketRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.SlidingWindowConfig;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.github.jacksonvieira.ratelimiter.redis.RedisFixedWindowRateLimiter;
import io.github.jacksonvieira.ratelimiter.redis.RedisSlidingWindowCounterRateLimiter;
import io.github.jacksonvieira.ratelimiter.redis.RedisSlidingWindowLogRateLimiter;
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
    RateLimiter rateLimiter(GatewayProperties properties, RateLimiterFactory factory) {
        return factory.create(properties.rateLimit(), null);
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.rate-limit.store", havingValue = "memory", matchIfMissing = true)
    RateLimiterFactory inMemoryRateLimiterFactory() {
        return (rateLimit, scope) -> inMemory(rateLimit);
    }

    static RateLimiter inMemory(GatewayProperties.RateLimit rateLimit) {
        return switch (rateLimit.algorithm()) {
            case TOKEN_BUCKET -> new InMemoryTokenBucketRateLimiter(rateLimit.toTokenBucketConfig());
            case FIXED_WINDOW -> new InMemoryFixedWindowRateLimiter(
                    new FixedWindowConfig(rateLimit.capacity(), rateLimit.refillPeriod()));
            case SLIDING_WINDOW_COUNTER -> new InMemorySlidingWindowCounterRateLimiter(
                    new SlidingWindowConfig(rateLimit.capacity(), rateLimit.refillPeriod()));
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
        RateLimiterFactory redisRateLimiterFactory(StatefulRedisConnection<String, String> connection) {
            return (rateLimit, scope) -> withCircuitBreaker(rateLimit, redis(connection, rateLimit, scope));
        }

        private static RateLimiter withCircuitBreaker(GatewayProperties.RateLimit rateLimit, RateLimiter redis) {
            GatewayProperties.CircuitBreaker breaker = rateLimit.redis().circuitBreaker();
            if (!breaker.enabled()) {
                return redis;
            }
            return new CircuitBreakerRateLimiter(
                    redis, inMemory(rateLimit), breaker.failureThreshold(), breaker.openDuration());
        }

        private static RateLimiter redis(
                StatefulRedisConnection<String, String> connection,
                GatewayProperties.RateLimit rateLimit,
                String scope) {
            return switch (rateLimit.algorithm()) {
                case TOKEN_BUCKET -> new RedisTokenBucketRateLimiter(
                        connection, rateLimit.toTokenBucketConfig(), prefix("rl:tb:", scope));
                case FIXED_WINDOW -> new RedisFixedWindowRateLimiter(
                        connection,
                        new FixedWindowConfig(rateLimit.capacity(), rateLimit.refillPeriod()),
                        prefix("rl:fw:", scope));
                case SLIDING_WINDOW_COUNTER -> new RedisSlidingWindowCounterRateLimiter(
                        connection,
                        new SlidingWindowConfig(rateLimit.capacity(), rateLimit.refillPeriod()),
                        prefix("rl:swc:", scope));
                case SLIDING_WINDOW_LOG -> new RedisSlidingWindowLogRateLimiter(
                        connection,
                        new SlidingWindowConfig(rateLimit.capacity(), rateLimit.refillPeriod()),
                        prefix("rl:swl:", scope));
            };
        }

        private static String prefix(String base, String scope) {
            return scope == null ? base : base + scope + ":";
        }
    }
}
