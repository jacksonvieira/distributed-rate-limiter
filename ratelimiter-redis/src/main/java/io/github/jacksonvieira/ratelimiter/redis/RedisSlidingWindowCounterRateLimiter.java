package io.github.jacksonvieira.ratelimiter.redis;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.github.jacksonvieira.ratelimiter.core.SlidingWindowConfig;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Sliding window counter rate limiter whose counters live in Redis, so the limit is shared by every instance.
 * The load is estimated from the current and previous fixed windows, like the in-memory version.
 *
 * <p>Each decision is one atomic Lua script ({@code sliding_window_counter.lua}) that reads the time from Redis,
 * so instances with skewed clocks agree on where the windows are. Counters expire once the next window is over.
 */
public final class RedisSlidingWindowCounterRateLimiter implements RateLimiter {

    static final String SCRIPT = LuaScript.readResource(
            RedisSlidingWindowCounterRateLimiter.class, "sliding_window_counter.lua");
    private static final String DEFAULT_KEY_PREFIX = "rl:swc:";

    private final SlidingWindowConfig config;
    private final String keyPrefix;
    private final String limitArg;
    private final String windowArg;
    private final LuaScript script;

    public RedisSlidingWindowCounterRateLimiter(StatefulRedisConnection<String, String> connection, SlidingWindowConfig config) {
        this(connection, config, DEFAULT_KEY_PREFIX);
    }

    public RedisSlidingWindowCounterRateLimiter(
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
                new String[] {keyPrefix + key}, new String[] {limitArg, windowArg, Long.toString(permits)});
        boolean allowed = reply.get(0) == 1L;
        Duration retryAfter = allowed ? Duration.ZERO : Duration.ofNanos(reply.get(2) * 1_000);
        return new RateLimitResult(allowed, config.limit(), reply.get(1), retryAfter);
    }
}
