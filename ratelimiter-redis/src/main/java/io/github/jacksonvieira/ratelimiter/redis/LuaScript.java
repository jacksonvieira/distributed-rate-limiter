package io.github.jacksonvieira.ratelimiter.redis;

import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A Lua script loaded into Redis once and then run by its SHA, reloading it if Redis forgets it.
 */
final class LuaScript {

    private final RedisCommands<String, String> redis;
    private final String source;
    private volatile String sha;

    LuaScript(RedisCommands<String, String> redis, String source) {
        this.redis = redis;
        this.source = source;
        this.sha = redis.scriptLoad(source);
    }

    List<Long> run(String[] keys, String[] args) {
        try {
            return redis.evalsha(sha, ScriptOutputType.MULTI, keys, args);
        } catch (RedisNoScriptException e) {
            // Redis lost its script cache (restart, failover, SCRIPT FLUSH): load it again and retry once.
            sha = redis.scriptLoad(source);
            return redis.evalsha(sha, ScriptOutputType.MULTI, keys, args);
        }
    }

    static String readResource(Class<?> owner, String name) {
        try (InputStream in = owner.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Lua script not found on the classpath: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
