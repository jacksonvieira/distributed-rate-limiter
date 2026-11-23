package io.github.jacksonvieira.ratelimiter.redis;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.github.jacksonvieira.ratelimiter.core.TokenBucketConfig;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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

    static final String SCRIPT = loadScript("token_bucket.lua");
    private static final String DEFAULT_KEY_PREFIX = "rl:tb:";

    private final RedisCommands<String, String> redis;
    private final TokenBucketConfig config;
    private final String keyPrefix;
    private final String[] configArgs;
    private volatile String scriptSha;

    public RedisTokenBucketRateLimiter(StatefulRedisConnection<String, String> connection, TokenBucketConfig config) {
        this(connection, config, DEFAULT_KEY_PREFIX);
    }

    public RedisTokenBucketRateLimiter(
            StatefulRedisConnection<String, String> connection, TokenBucketConfig config, String keyPrefix) {
        this.redis = Objects.requireNonNull(connection, "connection").sync();
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
        this.scriptSha = redis.scriptLoad(SCRIPT);
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
        String[] keys = {redisKey};
        String[] args = {configArgs[0], configArgs[1], configArgs[2], permits};
        try {
            return redis.evalsha(scriptSha, ScriptOutputType.MULTI, keys, args);
        } catch (RedisNoScriptException e) {
            // Redis lost its script cache (restart, failover, SCRIPT FLUSH): load it again and retry once.
            scriptSha = redis.scriptLoad(SCRIPT);
            return redis.evalsha(scriptSha, ScriptOutputType.MULTI, keys, args);
        }
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

    private static String loadScript(String name) {
        try (InputStream in = RedisTokenBucketRateLimiter.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Lua script not found on the classpath: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
