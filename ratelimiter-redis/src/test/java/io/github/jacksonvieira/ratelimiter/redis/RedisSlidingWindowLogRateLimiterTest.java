package io.github.jacksonvieira.ratelimiter.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.jacksonvieira.ratelimiter.core.RateLimitResult;
import io.github.jacksonvieira.ratelimiter.core.SlidingWindowConfig;
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
 * Runs the limiter against a real Redis in Docker. Time comes from Redis, so tests use a long window
 * unless they need to see permits leave it.
 */
@Testcontainers
class RedisSlidingWindowLogRateLimiterTest {

    private static final Duration LONG_WINDOW = Duration.ofHours(1);

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

    private static RedisSlidingWindowLogRateLimiter limiter(long limit, Duration window) {
        return new RedisSlidingWindowLogRateLimiter(connection, new SlidingWindowConfig(limit, window));
    }

    @Test
    void allowsUpToTheLimitThenRejects() {
        RedisSlidingWindowLogRateLimiter limiter = limiter(3, LONG_WINDOW);

        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(2);
        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(1);
        assertThat(limiter.tryAcquire("user").remaining()).isZero();

        RateLimitResult rejected = limiter.tryAcquire("user");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.limit()).isEqualTo(3);
        assertThat(rejected.retryAfter()).isPositive().isLessThanOrEqualTo(LONG_WINDOW);
    }

    @Test
    void keysHaveIndependentLogs() {
        RedisSlidingWindowLogRateLimiter limiter = limiter(1, LONG_WINDOW);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void aRejectedMultiPermitRequestConsumesNothing() {
        RedisSlidingWindowLogRateLimiter limiter = limiter(5, LONG_WINDOW);

        assertThat(limiter.tryAcquire("user", 4).allowed()).isTrue();
        assertThat(limiter.tryAcquire("user", 2).allowed()).isFalse();
        assertThat(limiter.tryAcquire("user", 1).remaining()).isZero();
    }

    @Test
    void permitsLeaveTheWindowOneByOneAndRetryAfterIsHonest() throws InterruptedException {
        RedisSlidingWindowLogRateLimiter limiter = limiter(2, Duration.ofMillis(600));

        limiter.tryAcquire("user");
        Thread.sleep(300);
        limiter.tryAcquire("user");
        RateLimitResult rejected = limiter.tryAcquire("user");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isLessThanOrEqualTo(Duration.ofMillis(300));

        Thread.sleep(rejected.retryAfter().toMillis() + 50);

        assertThat(limiter.tryAcquire("user").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user").allowed()).isFalse();
    }

    @Test
    void forgetsEverythingAfterIdleWindows() throws InterruptedException {
        RedisSlidingWindowLogRateLimiter limiter = limiter(2, Duration.ofMillis(200));

        limiter.tryAcquire("user");
        limiter.tryAcquire("user");
        Thread.sleep(500);

        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(1);
    }

    @Test
    void theLogExpiresAfterTheWindow() {
        RedisSlidingWindowLogRateLimiter limiter = limiter(2, LONG_WINDOW);

        limiter.tryAcquire("user");

        long ttlMillis = connection.sync().pttl("rl:swl:user");
        assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(LONG_WINDOW.toMillis() + 1);
    }

    @Test
    void limiterInstancesSharingRedisShareTheLimit() {
        RedisSlidingWindowLogRateLimiter first = limiter(2, LONG_WINDOW);
        RedisSlidingWindowLogRateLimiter second = limiter(2, LONG_WINDOW);

        assertThat(first.tryAcquire("user").allowed()).isTrue();
        assertThat(second.tryAcquire("user").allowed()).isTrue();
        assertThat(first.tryAcquire("user").allowed()).isFalse();
    }

    @Test
    void neverGrantsMoreThanTheLimitUnderConcurrency() throws Exception {
        RedisSlidingWindowLogRateLimiter limiter = limiter(20, LONG_WINDOW);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            results.add(pool.submit(() -> {
                start.await();
                int granted = 0;
                for (int i = 0; i < 10; i++) {
                    if (limiter.tryAcquire("shared").allowed()) {
                        granted++;
                    }
                }
                return granted;
            }));
        }
        start.countDown();
        int granted = 0;
        for (Future<Integer> result : results) {
            granted += result.get();
        }
        pool.shutdown();

        assertThat(granted).isEqualTo(20);
    }

    @Test
    void rejectsInvalidArguments() {
        RedisSlidingWindowLogRateLimiter limiter = limiter(3, LONG_WINDOW);

        assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire(" ", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire("a", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire("a", 4));
    }
}
