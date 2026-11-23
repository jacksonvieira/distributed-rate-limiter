package io.github.jacksonvieira.ratelimiter.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.TokenBucketConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the limiter against a real Redis in Docker.
 *
 * <p>Time comes from the Redis server, so these tests cannot fake the clock like the in-memory ones:
 * refill tests use short periods and real waits, everything else uses a one-hour period so no token
 * is refilled while the test runs.
 */
@Testcontainers
class RedisTokenBucketRateLimiterTest {

    private static final Duration NO_REFILL = Duration.ofHours(1);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static RedisClient client;
    static StatefulRedisConnection<String, String> connection;

    @BeforeAll
    static void connect() {
        client = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        connection = client.connect();
    }

    @AfterAll
    static void disconnect() {
        connection.close();
        client.shutdown();
    }

    @BeforeEach
    void cleanRedis() {
        connection.sync().flushall();
    }

    private static RedisTokenBucketRateLimiter limiter(long capacity, long refillTokens, Duration period) {
        return new RedisTokenBucketRateLimiter(connection, new TokenBucketConfig(capacity, refillTokens, period));
    }

    @Test
    void allowsABurstUpToCapacityThenRejects() {
        RedisTokenBucketRateLimiter limiter = limiter(3, 1, NO_REFILL);

        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(2);
        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(1);
        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(0);

        RateLimitResult rejected = limiter.tryAcquire("user");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.limit()).isEqualTo(3);
        assertThat(rejected.remaining()).isZero();
    }

    @Test
    void retryAfterIsTheTimeUntilTheMissingTokensArrive() {
        RedisTokenBucketRateLimiter limiter = limiter(1, 1, NO_REFILL);
        limiter.tryAcquire("user");

        Duration retryAfter = limiter.tryAcquire("user").retryAfter();

        assertThat(retryAfter).isBetween(NO_REFILL.minusSeconds(5), NO_REFILL);
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        // One token every 100 ms.
        RedisTokenBucketRateLimiter limiter = limiter(2, 1, Duration.ofMillis(100));
        limiter.tryAcquire("user");
        limiter.tryAcquire("user");
        assertThat(limiter.tryAcquire("user").allowed()).isFalse();

        Thread.sleep(150);

        assertThat(limiter.tryAcquire("user").allowed()).isTrue();
    }

    @Test
    void neverRefillsAboveCapacity() throws InterruptedException {
        RedisTokenBucketRateLimiter limiter = limiter(2, 10, Duration.ofMillis(10));
        limiter.tryAcquire("user");

        Thread.sleep(100); // would earn 100 tokens without the cap

        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(1);
    }

    @Test
    void multiplePermitsAreAllOrNothing() {
        RedisTokenBucketRateLimiter limiter = limiter(5, 1, NO_REFILL);
        limiter.tryAcquire("user", 3);

        RateLimitResult rejected = limiter.tryAcquire("user", 3);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isEqualTo(2);
        assertThat(limiter.tryAcquire("user", 2).allowed()).isTrue();
    }

    @Test
    void keysHaveIndependentBuckets() {
        RedisTokenBucketRateLimiter limiter = limiter(1, 1, NO_REFILL);
        limiter.tryAcquire("alice");

        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();
        assertThat(limiter.tryAcquire("bob").allowed()).isTrue();
    }

    @Test
    void instancesShareTheSameBucket() {
        // Two limiters on two connections behave like two gateway instances.
        try (StatefulRedisConnection<String, String> other = client.connect()) {
            TokenBucketConfig config = new TokenBucketConfig(2, 1, NO_REFILL);
            RedisTokenBucketRateLimiter first = new RedisTokenBucketRateLimiter(connection, config);
            RedisTokenBucketRateLimiter second = new RedisTokenBucketRateLimiter(other, config);

            assertThat(first.tryAcquire("user").allowed()).isTrue();
            assertThat(second.tryAcquire("user").allowed()).isTrue();
            assertThat(first.tryAcquire("user").allowed()).isFalse();
        }
    }

    @Test
    void idleBucketsExpireOnceTheyWouldBeFull() {
        RedisTokenBucketRateLimiter limiter = limiter(10, 1, Duration.ofSeconds(1));
        limiter.tryAcquire("user", 4);

        long ttlMillis = connection.sync().pttl("rl:tb:user");

        // 4 tokens missing at 1 token per second: full again in about 4 seconds.
        assertThat(ttlMillis).isBetween(3_000L, 4_000L);
    }

    @Test
    void concurrentRequestsNeverGetMorePermitsThanTheCapacity() throws Exception {
        RedisTokenBucketRateLimiter limiter = limiter(50, 1, NO_REFILL);
        int threads = 8;
        int requestsPerThread = 20;
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    int allowed = 0;
                    for (int i = 0; i < requestsPerThread; i++) {
                        if (limiter.tryAcquire("hot-key").allowed()) {
                            allowed++;
                        }
                    }
                    return allowed;
                }));
            }
            start.countDown();

            int totalAllowed = 0;
            for (Future<Integer> result : results) {
                totalAllowed += result.get();
            }
            assertThat(totalAllowed).isEqualTo(50);
        }
    }

    @Test
    void reloadsTheScriptWhenRedisForgetsIt() {
        RedisTokenBucketRateLimiter limiter = limiter(2, 1, NO_REFILL);
        connection.sync().scriptFlush();

        assertThat(limiter.tryAcquire("user").allowed()).isTrue();
    }

    @Test
    void rejectsInvalidArguments() {
        RedisTokenBucketRateLimiter limiter = limiter(2, 1, NO_REFILL);

        assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire("user", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire("user", 3));
    }
}
