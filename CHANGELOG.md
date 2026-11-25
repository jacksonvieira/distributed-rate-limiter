# Changelog

## Unreleased

### Security
- Container image runs as numeric non-root user, with container-aware JVM flags; Trivy scan before publishing; restricted `securityContext` in the Kubernetes manifests
- `gateway.plan-by-api-key` resolves the plan from the API key, so clients cannot choose their own plan with the plan header

### Documentation
- Architecture decision records in `docs/adr`

## 0.3.0 - 2026-11-25

### Security
- Dependabot updates and CodeQL analysis
- Security headers and request body size limits in the gateway
- Rate limiting on the admin API
- TLS test coverage

### Resilience
- Redis commands fail fast while disconnected (`gateway.rate-limit.redis.command-timeout`, default 1s), found with a Toxiproxy outage test

### Developer experience
- `Makefile` wrapping the common build, run, cluster and benchmark tasks

### Operations
- CI workflow publishing the gateway and admin API images to GHCR on `main` and version tags
- Kustomize manifests in `deploy/k8s` for the full stack

### Testing
- PIT mutation testing for `ratelimiter-core` (`make mutation`, 80% threshold)
- Contract tests: the admin API and the gateway verify the same `contracts/rules-list.json` fixture

### Data
- jOOQ type-safe queries in the admin API, with classes generated from the Flyway migrations at build time
- `V2` migration seeding two default rules (`default-api`, `default-fallback`)

## 0.2.0 - 2026-11-24

### Observability
- Distributed tracing with Micrometer Tracing and OpenTelemetry in the gateway and the admin API (W3C `traceparent`)
- `[application,traceId,spanId]` on every log line
- Spans exported over OTLP to Jaeger in the compose cluster; nginx forwards the trace context
- `X-Trace-Id` response header on the gateway

## 0.1.0

First release.

### Rate limiting
- Token bucket, fixed window, sliding window log and sliding window counter, each in memory and on Redis
  (atomic Lua scripts, shared state across instances).
- Circuit breaker with local fallback and a configurable fail mode when Redis is unavailable.
- Idle key eviction for the in-memory limiters.

### Gateway
- Reactive reverse proxy with per-route rules, key strategy (IP or header) and plans.
- Micrometer metrics, Prometheus scrape endpoint and a Grafana dashboard.
- Virtual threads for the blocking limiter calls.
- Hot reload of rules from the admin API, keeping counters of unchanged rules.

### Admin API
- REST CRUD for rules in PostgreSQL (Flyway), hexagonal layering enforced by ArchUnit.
- DTOs with MapStruct, OpenAPI/Swagger UI, HATEOAS links and an optional API key.

### Tooling
- Docker Compose cluster: three gateways, nginx, Redis, PostgreSQL, admin API, Prometheus and Grafana.
- JMH benchmarks, a distributed load check script, JaCoCo coverage and a Spotless check in CI.
