# distributed-rate-limiter

[![CI](https://github.com/jacksonvieira/distributed-rate-limiter/actions/workflows/ci.yml/badge.svg)](https://github.com/jacksonvieira/distributed-rate-limiter/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-25-blue)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A distributed rate limiter and lightweight API gateway built with Java and Redis.

The goal is a rate limiter that behaves the same whether one or many gateway instances
are running, offers several algorithms with documented trade-offs, and stays fast and
predictable under load. Each algorithm ships with an in-memory reference implementation
and a Redis-backed implementation that shares state across instances through atomic Lua scripts.

> **Status:** early development. The in-memory token bucket is done; the Redis implementation
> and the gateway are next. See the [roadmap](#roadmap).

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
| `ratelimiter-core` | in progress | Algorithms, with in-memory and Redis-backed implementations |
| `gateway` | planned | Reactive reverse proxy applying rules per route, key and plan |
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

## Getting started

Requirements: JDK 25, Maven 3.9+, Docker (for Redis, from phase 2).

```bash
mvn verify                 # build and run the tests
docker compose up -d       # start Redis locally
```

## Roadmap

1. **MVP:** in-memory token bucket ✅, gateway with a single route, README
2. **Distributed:** Redis implementation with Lua scripts, Testcontainers, Docker Compose with 3 gateway instances behind a load balancer
3. **More algorithms:** fixed window, sliding window log and sliding window counter; rule configuration in YAML
4. **Observability:** Micrometer + Prometheus metrics, Grafana dashboard, `X-RateLimit-*` and `Retry-After` headers
5. **Dynamic rules:** admin API (gRPC), rules in PostgreSQL with Flyway, hot reload
6. **Resilience:** local fallback when Redis is down, fail-open / fail-closed modes, circuit breaker
7. **Benchmarks:** published results comparing the algorithms

## License

[MIT](LICENSE)
