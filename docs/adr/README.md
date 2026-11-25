# Architecture decision records

Short notes on the decisions that shape the project, with the context and the trade-offs.

| ADR | Decision |
| --- | --- |
| [0001](0001-hexagonal-architecture.md) | Hexagonal architecture with an ArchUnit-enforced dependency rule |
| [0002](0002-redis-lua-for-atomicity.md) | Atomic Lua scripts for the distributed limiters |
| [0003](0003-fail-mode-and-circuit-breaker.md) | Fail mode and circuit breaker when the limiter backend fails |
| [0004](0004-jooq-and-flyway.md) | jOOQ and Flyway, with generated classes kept inside the adapter |
| [0005](0005-rules-hot-reload-by-polling.md) | Rules hot reload by polling the admin API |
