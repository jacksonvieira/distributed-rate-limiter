# 0002. Atomic Lua scripts for the distributed limiters

## Context
Several gateway instances share the same limits. A read-modify-write done from the client would race between instances and allow more requests than the configured capacity.

## Decision
Each Redis algorithm runs as a single Lua script, so reading the state, computing the new state and writing it back is one atomic step on the server. The scripts use the Redis clock (`TIME`) instead of the caller's, so instances with clock skew still agree.

## Consequences
- The limit holds across instances, and `SharedLimitAcrossInstancesTest` checks it against a real Redis.
- The logic lives in two languages (Java in memory, Lua in Redis), so both implementations are held to the same behavioural expectations.
- Every decision costs one Redis round trip.
