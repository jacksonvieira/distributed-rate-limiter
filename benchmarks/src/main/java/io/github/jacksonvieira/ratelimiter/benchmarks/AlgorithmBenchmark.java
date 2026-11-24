package io.github.jacksonvieira.ratelimiter.benchmarks;

import io.github.jacksonvieira.ratelimiter.core.FixedWindowConfig;
import io.github.jacksonvieira.ratelimiter.core.InMemoryFixedWindowRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.InMemorySlidingWindowCounterRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.InMemorySlidingWindowLogRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.InMemoryTokenBucketRateLimiter;
import io.github.jacksonvieira.ratelimiter.core.RateLimiter;
import io.github.jacksonvieira.ratelimiter.core.SlidingWindowConfig;
import io.github.jacksonvieira.ratelimiter.core.TokenBucketConfig;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Throughput of {@code tryAcquire} for each in-memory algorithm, with all threads hitting one hot key or
 * spreading over many keys. The limit is high so the benchmark measures the algorithm, not rejections.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(4)
public class AlgorithmBenchmark {

    private static final long LIMIT = 1_000_000_000L;
    private static final int KEYS = 10_000;

    @Param({"TOKEN_BUCKET", "FIXED_WINDOW", "SLIDING_WINDOW_LOG", "SLIDING_WINDOW_COUNTER"})
    public String algorithm;

    private RateLimiter limiter;
    private String[] keys;

    @Setup
    public void setUp() {
        Duration period = Duration.ofSeconds(1);
        limiter = switch (algorithm) {
            case "TOKEN_BUCKET" -> new InMemoryTokenBucketRateLimiter(new TokenBucketConfig(LIMIT, LIMIT, period));
            case "FIXED_WINDOW" -> new InMemoryFixedWindowRateLimiter(new FixedWindowConfig(LIMIT, period));
            case "SLIDING_WINDOW_LOG" ->
                    new InMemorySlidingWindowLogRateLimiter(new SlidingWindowConfig(LIMIT, period));
            case "SLIDING_WINDOW_COUNTER" ->
                    new InMemorySlidingWindowCounterRateLimiter(new SlidingWindowConfig(LIMIT, period));
            default -> throw new IllegalArgumentException(algorithm);
        };
        keys = new String[KEYS];
        for (int i = 0; i < KEYS; i++) {
            keys[i] = "client-" + i;
        }
    }

    @Benchmark
    public Object singleKey() {
        return limiter.tryAcquire("hot");
    }

    @Benchmark
    public Object manyKeys() {
        return limiter.tryAcquire(keys[ThreadLocalRandom.current().nextInt(KEYS)]);
    }
}
