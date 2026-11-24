package io.github.jacksonvieira.ratelimiter.redis;

import io.github.jacksonvieira.ratelimiter.core.FixedWindowConfig;
import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Fixed window rate limiter whose counters live in Redis, so the limit is shared by every instance.
 *
 * <p>Each decision is one atomic Lua script ({@code fixed_window.lua}) that reads the time from Redis,
 * so instances with skewed clocks agree on where the windows are. Counters expire with their window.
 */
public final class RedisFixedWindowRateLimiter implements RateLimiter {

    static final String SCRIPT = LuaScript.readResource(RedisFixedWindowRateLimiter.class, "fixed_window.lua");
    private static final String DEFAULT_KEY_PREFIX = "rl:fw:";

    private final FixedWindowConfig config;
    private final String keyPrefix;
    private final String limitArg;
    private final String windowArg;
    private final LuaScript script;

    public RedisFixedWindowRateLimiter(StatefulRedisConnection<String, String> connection, FixedWindowConfig config) {
        this(connection, config, DEFAULT_KEY_PREFIX);
    }

    public RedisFixedWindowRateLimiter(
            StatefulRedisConnection<String, String> connection, FixedWindowConfig config, String keyPrefix) {
        Objects.requireNonNull(connection, "connection");
        this.config = Objects.requireNonNull(config, "config");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");

        long windowMicros = config.window().toNanos() / 1_000;
        if (windowMicros < 1) {
            throw new IllegalArgumentException("window must be at least 1 microsecond for the Redis limiter");
        }
        this.limitArg = Long.toString(config.limit());
        this.windowArg = Long.toString(windowMicros);
        this.script = new LuaScript(connection.sync(), SCRIPT);
    }

    @Override
    public RateLimitResult tryAcquire(String key, long permits) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be null or blank");
        }
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be at least 1, got " + permits);
        }
        if (permits > config.limit()) {
            throw new IllegalArgumentException(
                    "permits (" + permits + ") exceed the window limit (" + config.limit() + ")");
        }

        List<Long> reply = script.run(
                new String[] {keyPrefix + key}, new String[] {limitArg, windowArg, Long.toString(permits)});
        boolean allowed = reply.get(0) == 1L;
        Duration retryAfter = allowed ? Duration.ZERO : Duration.ofNanos(reply.get(2) * 1_000);
        return new RateLimitResult(allowed, config.limit(), reply.get(1), retryAfter);
    }
}
