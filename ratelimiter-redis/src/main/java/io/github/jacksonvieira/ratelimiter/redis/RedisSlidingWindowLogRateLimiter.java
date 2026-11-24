package io.github.jacksonvieira.ratelimiter.redis;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.github.jacksonvieira.ratelimiter.core.SlidingWindowConfig;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sliding window log rate limiter whose log lives in a Redis sorted set, so the limit is shared by every
 * instance. Exact like the in-memory version, with memory proportional to the permits granted in the window.
 *
 * <p>Each decision is one atomic Lua script ({@code sliding_window_log.lua}) that reads the time from Redis,
 * so instances with skewed clocks agree on the window. The log expires once the window has passed.
 */
public final class RedisSlidingWindowLogRateLimiter implements RateLimiter {

    static final String SCRIPT = LuaScript.readResource(
            RedisSlidingWindowLogRateLimiter.class, "sliding_window_log.lua");
    private static final String DEFAULT_KEY_PREFIX = "rl:swl:";

    private final SlidingWindowConfig config;
    private final String keyPrefix;
    private final String limitArg;
    private final String windowArg;
    private final LuaScript script;

    public RedisSlidingWindowLogRateLimiter(StatefulRedisConnection<String, String> connection, SlidingWindowConfig config) {
        this(connection, config, DEFAULT_KEY_PREFIX);
    }

    public RedisSlidingWindowLogRateLimiter(
            StatefulRedisConnection<String, String> connection, SlidingWindowConfig config, String keyPrefix) {
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
                new String[] {keyPrefix + key},
                new String[] {limitArg, windowArg, Long.toString(permits), UUID.randomUUID().toString()});
        boolean allowed = reply.get(0) == 1L;
        Duration retryAfter = allowed ? Duration.ZERO : Duration.ofNanos(reply.get(2) * 1_000);
        return new RateLimitResult(allowed, config.limit(), reply.get(1), retryAfter);
    }
}
