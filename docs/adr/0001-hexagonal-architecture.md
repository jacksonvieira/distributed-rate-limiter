# 0001. Hexagonal architecture

## Context
The algorithms must be testable without Redis, a web server or a database, and the storage and the entry points must be replaceable.

## Decision
- `ratelimiter-core` holds the `RateLimiter` port and the in-memory algorithms, with no framework or I/O dependency.
- `ratelimiter-redis` and the in-memory classes are adapters of that port. The `gateway` is a driving adapter that calls it.
- The admin API is split into `domain`, `application` (use cases), `adapter.in.web` and `adapter.out.jooq`.
- ArchUnit tests fail the build when the core depends on an adapter or framework, or when jOOQ types leak outside `adapter.out.jooq`.

## Consequences
- Algorithms are unit tested in isolation and swapped through configuration.
- A little more mapping code (for example `RuleResponse` and the repository mapper), accepted to keep the domain free of persistence and HTTP types.
