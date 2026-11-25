# 0004. jOOQ and Flyway

## Context
Table and column names were repeated as string constants in hand-written SQL, and the schema had no history.

## Decision
- Flyway migrations (`db/migration`) are the single source of truth for the schema and the seed data.
- jOOQ classes are generated at build time from those scripts (DDL parsing, no database needed), and queries are type checked.
- The generated classes are used only inside `adapter.out.jooq`. The domain keeps its own `Rule` record and the adapter maps to it.

## Consequences
- Renaming a column breaks the compilation instead of a runtime query.
- Pointing the domain at the generated classes was rejected: the core would depend on the persistence adapter and its model would be tied to the table layout.
- The DDL parser translates through H2, so migrations must avoid PostgreSQL-only syntax that it cannot read.
