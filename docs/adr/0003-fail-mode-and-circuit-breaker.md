# 0003. Fail mode and circuit breaker

## Context
The limiter backend can fail. The gateway must not hang on it, and the right reaction depends on the service: protect the backend or keep serving.

## Decision
- Redis commands time out quickly (`command-timeout`, 1s by default) and are rejected at once while disconnected, instead of waiting for the driver's long default. A Toxiproxy outage test found this.
- `on-error` chooses `fail-open` (let requests through) or `fail-closed` (`503`).
- An optional circuit breaker opens after consecutive failures and limits requests with a local in-memory limiter until Redis recovers.

## Consequences
- During an outage, limits are enforced per instance rather than globally (the local fallback), which is looser but still bounded.
- The behaviour is visible in the `limiter_failed` metric and in the logs.
