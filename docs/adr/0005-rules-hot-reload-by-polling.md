# 0005. Rules hot reload by polling

## Context
Rules are managed at runtime through the admin API and every gateway instance must pick up changes without a restart.

## Decision
Each gateway polls `GET /api/rules` periodically (`refresh-interval`), builds the new rule set and swaps it. Unchanged rules keep their counters. If the admin API is unreachable or returns invalid data, the gateway keeps the current rules.

## Consequences
- Simple and robust: no extra broker, and a down admin API never takes the gateway down.
- Changes take up to one refresh interval to apply.
- The response format is a contract, verified on both sides by the tests that share `contracts/rules-list.json`.
- A push mechanism (events or SSE) remains possible later without changing the rule model.
