package io.github.jacksonvieira.ratelimiter.redis;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.github.jacksonvieira.ratelimiter.core.TokenBucketConfig;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Token bucket rate limiter whose buckets live in Redis, shared by every instance that uses the
 * same Redis server.
 *
 * <p>Each decision is a single Lua script ({@code token_bucket.lua}) executed atomically by Redis:
 * refill, check and consume happen without any other command interleaving, so concurrent instances
 * never over-grant permits. The script reads the time from Redis itself, so instances with skewed
 * clocks still agree on how many tokens a bucket has.
 *
 * <p>Idle buckets expire once they would be full again: a missing key means a full bucket,
 * so expiring it changes nothing except freeing memory.
 */
public final class RedisTokenBucketRateLimiter implements RateLimiter {

    static final String SCRIPT = LuaScript.readResource(RedisTokenBucketRateLimiter.class, "token_bucket.lua");
    private static final String DEFAULT_KEY_PREFIX = "rl:tb:";

    private final LuaScript script;
    private final TokenBucketConfig config;
    private final String keyPrefix;
    private final String[] configArgs;

    public RedisTokenBucketRateLimiter(StatefulRedisConnection<String, String> connection, TokenBucketConfig config) {
        this(connection, config, DEFAULT_KEY_PREFIX);
    }

    public RedisTokenBucketRateLimiter(
            StatefulRedisConnection<String, String> connection, TokenBucketConfig config, String keyPrefix) {
        Objects.requireNonNull(connection, "connection");
        this.config = Objects.requireNonNull(config, "config");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");

        long refillPeriodMicros = config.refillPeriod().toNanos() / 1_000;
        if (refillPeriodMicros < 1) {
            throw new IllegalArgumentException("refillPeriod must be at least 1 microsecond for the Redis limiter");
        }
        this.configArgs = new String[] {
                Long.toString(config.capacity()),
                Long.toString(config.refillTokens()),
                Long.toString(refillPeriodMicros)
        };
        this.script = new LuaScript(connection.sync(), SCRIPT);
    }

    @Override
    public RateLimitResult tryAcquire(String key, long permits) {
        validate(key, permits);

        List<Long> reply = execute(keyPrefix + key, Long.toString(permits));
        boolean allowed = reply.get(0) == 1L;
        long remaining = reply.get(1);
        Duration retryAfter = Duration.ofNanos(reply.get(2) * 1_000);

        return allowed
                ? new RateLimitResult(true, config.capacity(), remaining, Duration.ZERO)
                : new RateLimitResult(false, config.capacity(), remaining, retryAfter);
    }

    private List<Long> execute(String redisKey, String permits) {
        return script.run(new String[] {redisKey}, new String[] {configArgs[0], configArgs[1], configArgs[2], permits});
    }

    private void validate(String key, long permits) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be null or blank");
        }
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be at least 1, got " + permits);
        }
        if (permits > config.capacity()) {
            throw new IllegalArgumentException(
                    "permits (" + permits + ") exceed the bucket capacity (" + config.capacity() + ")");
        }
    }
}
