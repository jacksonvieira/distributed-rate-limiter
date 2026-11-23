# distributed-rate-limiter

[![CI](https://github.com/jacksonvieira/distributed-rate-limiter/actions/workflows/ci.yml/badge.svg)](https://github.com/jacksonvieira/distributed-rate-limiter/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-25-blue)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A distributed rate limiter and lightweight API gateway built with Java and Redis.

The goal is a rate limiter that behaves the same whether one or many gateway instances
are running, offers several algorithms with documented trade-offs, and stays fast and
predictable under load. Each algorithm ships with an in-memory reference implementation
and a Redis-backed implementation that shares state across instances through atomic Lua scripts.

> **Status:** early development. The in-memory and Redis token buckets and a single-route gateway
> are done; three gateway instances behind a load balancer share one limit. Next: a distributed integration test. See the [roadmap](#roadmap).

## Architecture

```text
                ┌───────────────────────────────┐
  clients ────► │  Gateway (Spring Boot 4)       │ ────► backend services
                │   • routing by path / host     │
                │   • rate limit filter          │
                └───────────────┬───────────────┘
                                │ atomic Lua scripts
                         ┌──────▼──────┐
                         │    Redis    │  ← state shared by N gateway instances
                         └─────────────┘

  Admin API (gRPC / REST) to change rules at runtime · Prometheus metrics + Grafana
```

### Modules

| Module | Status | Description |
| --- | --- | --- |
| `ratelimiter-core` | in progress | Algorithms and the `RateLimiter` port, with in-memory implementations |
| `ratelimiter-redis` | in progress | Redis-backed implementations that share state through atomic Lua scripts |
| `gateway` | in progress | Reactive reverse proxy applying rules per route, key and plan |
| `admin-api` | planned | Create and update rules at runtime, stored in PostgreSQL |
| `benchmarks` | planned | Load tests comparing algorithms on latency, throughput and accuracy |

## Algorithms

| Algorithm | Burst handling | Memory per key | Accuracy | Status |
| --- | --- | --- | --- | --- |
| Token bucket | Allows bursts up to capacity | O(1) | Exact | ✅ in-memory |
| Fixed window counter | Bursts at window edges | O(1) | Approximate | planned |
| Sliding window log | No bursts | O(requests) | Exact | planned |
| Sliding window counter | Smoothed | O(1) | Approximate | planned |

### Token bucket

Each key owns a bucket holding at most `capacity` tokens. Tokens are refilled continuously at
`refillTokens` per `refillPeriod`, and every request consumes one or more tokens.

The in-memory implementation is lock-free: each bucket is an immutable state object updated through
a compare-and-set loop, so concurrent requests for the same key never receive more permits than
the bucket holds. Refill is computed lazily on access, so there are no background threads.

## Usage

```java
// Bursts of up to 100 requests, sustained rate of 10 requests per second.
RateLimiter limiter = new InMemoryTokenBucketRateLimiter(
        new TokenBucketConfig(100, 10, Duration.ofSeconds(1)));

RateLimitResult result = limiter.tryAcquire("api-key-123");
if (!result.allowed()) {
    // Respond with 429 Too Many Requests and Retry-After: result.retryAfter()
}
```

`RateLimitResult` carries everything needed for the standard headers:
`X-RateLimit-Limit`, `X-RateLimit-Remaining` and `Retry-After`.

## Gateway

The `gateway` module is a reactive reverse proxy (Spring Boot 4, WebFlux). For now it serves one
route: every request under `path-prefix` goes through the rate limit and, if allowed, is forwarded
to `uri`. Request and response bodies are streamed, never buffered.

```yaml
gateway:
  route:
    path-prefix: /api            # /api/users -> http://localhost:8081/users
    uri: http://localhost:8081
    strip-prefix: true
    connect-timeout: 2s
    response-timeout: 30s
  rate-limit:
    capacity: 100                # burst size
    refill-tokens: 10            # sustained rate: 10 requests...
    refill-period: 1s            # ...per second
    key: ip                      # ip | header
    key-header: X-API-Key        # used when key = header; falls back to the client IP
    store: memory                # memory | redis (redis shares one limit across all instances)
    redis:
      uri: redis://localhost:6379   # used when store = redis
```

| Situation | Response |
| --- | --- |
| Allowed | Backend response as is, plus `X-RateLimit-Limit` and `X-RateLimit-Remaining` |
| Bucket empty | `429 Too Many Requests` with `Retry-After` (seconds); the backend is not called |
| Backend unreachable | `502 Bad Gateway` |
| Backend too slow | `504 Gateway Timeout` |
| Path outside the route | `404 Not Found`, not limited |

The `ip` key uses the TCP peer address, not `X-Forwarded-For`, because clients can set that header
to whatever they like. The gateway itself adds `X-Forwarded-For`, `X-Forwarded-Host` and
`X-Forwarded-Proto` for the backend, and drops hop-by-hop headers.

Known limitation: the in-memory limiter keeps one bucket per key and never evicts them, so memory
grows with the number of distinct clients. The Redis implementation will expire idle keys.

## Getting started

Requirements: JDK 25, Maven 3.9+, Docker (for Redis and the multi-instance setup).

```bash
mvn verify                 # build and run the tests
docker compose up -d       # start Redis locally
```

Run three gateway instances sharing one limit, behind an nginx load balancer on port 8000
(capacity 10, one token per second, clients identified by `X-API-Key`):

```bash
docker compose --profile cluster up -d --build
for i in $(seq 1 13); do
  curl -s -o /dev/null -D - -H 'X-API-Key: demo' localhost:8000/api/ | grep -iE '^HTTP|x-upstream'
done                       # 10 x 200, then 429, whichever instance answers
docker compose --profile cluster down
```

Run a single gateway in front of a backend on port 8081:

```bash
mvn package -DskipTests
java -jar gateway/target/gateway-0.1.0-SNAPSHOT.jar
curl -i http://localhost:8080/api/anything
```

## Roadmap

1. **MVP:** in-memory token bucket ✅, gateway with a single route ✅, README ✅
2. **Distributed:** Redis implementation with Lua scripts ✅, Testcontainers ✅, Docker Compose with 3 gateway instances behind a load balancer ✅
3. **More algorithms:** fixed window, sliding window log and sliding window counter; rule configuration in YAML
4. **Observability:** Micrometer + Prometheus metrics, Grafana dashboard, `X-RateLimit-*` and `Retry-After` headers
5. **Dynamic rules:** admin API (gRPC), rules in PostgreSQL with Flyway, hot reload
6. **Resilience:** local fallback when Redis is down, fail-open / fail-closed modes, circuit breaker
7. **Benchmarks:** published results comparing the algorithms

## License

[MIT](LICENSE)
