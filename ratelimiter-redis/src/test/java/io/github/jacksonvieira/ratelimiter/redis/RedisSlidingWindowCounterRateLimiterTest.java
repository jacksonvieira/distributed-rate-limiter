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
 * Runs the limiter against a real Redis in Docker. Time comes from Redis, so most tests use a one-hour
 * window; the ones about the fading previous window use a one-second window and tolerate timing slack.
 */
@Testcontainers
class RedisSlidingWindowCounterRateLimiterTest {

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

    private static RedisSlidingWindowCounterRateLimiter limiter(long limit, Duration window) {
        return new RedisSlidingWindowCounterRateLimiter(connection, new SlidingWindowConfig(limit, window));
    }

    // Sleeps until just after the next whole second, which is a window boundary for a one-second window.
    private static void sleepToWindowStart() throws InterruptedException {
        long now = System.currentTimeMillis();
        Thread.sleep(1000 - now % 1000 + 20);
    }

    @Test
    void allowsUpToTheLimitThenRejects() {
        RedisSlidingWindowCounterRateLimiter limiter = limiter(3, LONG_WINDOW);

        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(2);
        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(1);
        assertThat(limiter.tryAcquire("user").remaining()).isZero();

        RateLimitResult rejected = limiter.tryAcquire("user");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.limit()).isEqualTo(3);
        assertThat(rejected.retryAfter()).isPositive().isLessThanOrEqualTo(LONG_WINDOW.multipliedBy(2));
    }

    @Test
    void keysHaveIndependentCounters() {
        RedisSlidingWindowCounterRateLimiter limiter = limiter(1, LONG_WINDOW);

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void aRejectedMultiPermitRequestConsumesNothing() {
        RedisSlidingWindowCounterRateLimiter limiter = limiter(5, LONG_WINDOW);

        assertThat(limiter.tryAcquire("user", 4).allowed()).isTrue();
        assertThat(limiter.tryAcquire("user", 2).allowed()).isFalse();
        assertThat(limiter.tryAcquire("user", 1).remaining()).isZero();
    }

    @Test
    void doesNotAllowAFullBurstRightAfterTheWindowBoundary() throws InterruptedException {
        RedisSlidingWindowCounterRateLimiter limiter = limiter(10, Duration.ofSeconds(1));

        sleepToWindowStart();
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("user");
        }
        sleepToWindowStart();

        assertThat(limiter.tryAcquire("user").allowed()).isFalse();
    }

    @Test
    void thePreviousWindowFadesAndRetryAfterIsHonest() throws InterruptedException {
        RedisSlidingWindowCounterRateLimiter limiter = limiter(10, Duration.ofSeconds(1));

        sleepToWindowStart();
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("user");
        }
        sleepToWindowStart();
        RateLimitResult rejected = limiter.tryAcquire("user");
        assertThat(rejected.allowed()).isFalse();

        Thread.sleep(rejected.retryAfter().toMillis() + 50);

        assertThat(limiter.tryAcquire("user").allowed()).isTrue();
    }

    @Test
    void forgetsEverythingAfterIdleWindows() throws InterruptedException {
        RedisSlidingWindowCounterRateLimiter limiter = limiter(2, Duration.ofMillis(200));

        limiter.tryAcquire("user");
        limiter.tryAcquire("user");
        Thread.sleep(500);

        assertThat(limiter.tryAcquire("user").remaining()).isEqualTo(1);
    }

    @Test
    void theCounterExpiresAfterTheNextWindow() {
        RedisSlidingWindowCounterRateLimiter limiter = limiter(2, LONG_WINDOW);

        limiter.tryAcquire("user");

        long ttlMillis = connection.sync().pttl("rl:swc:user");
        assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(LONG_WINDOW.multipliedBy(2).toMillis());
    }

    @Test
    void limiterInstancesSharingRedisShareTheLimit() {
        RedisSlidingWindowCounterRateLimiter first = limiter(2, LONG_WINDOW);
        RedisSlidingWindowCounterRateLimiter second = limiter(2, LONG_WINDOW);

        assertThat(first.tryAcquire("user").allowed()).isTrue();
        assertThat(second.tryAcquire("user").allowed()).isTrue();
        assertThat(first.tryAcquire("user").allowed()).isFalse();
    }

    @Test
    void neverGrantsMoreThanTheLimitUnderConcurrency() throws Exception {
        RedisSlidingWindowCounterRateLimiter limiter = limiter(20, LONG_WINDOW);
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
        RedisSlidingWindowCounterRateLimiter limiter = limiter(3, LONG_WINDOW);

        assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire(" ", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire("a", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.tryAcquire("a", 4));
    }
}
