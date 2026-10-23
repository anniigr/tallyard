# ADR-007: JPA for aggregates with invariants, JDBC for the hot path

**Status:** Accepted (2026-10-23)

## Context

The codebase has two very different data access profiles: low-volume aggregates with rich invariants
(plans, periods, invoices, credit grants, webhook endpoints) and high-volume, set-oriented writes
(usage events, buckets, adjustments, outbox polling).

## Decision

- **JPA/Hibernate** for aggregates: entity methods enforce state transitions, `@Version` gives
  optimistic locking, `@Lock(PESSIMISTIC_READ/WRITE)` maps to `FOR SHARE` / `FOR UPDATE`, and the
  `jakarta.persistence.lock.timeout = -2` hint produces `SKIP LOCKED`. Collections are `Set`s with
  `@OrderBy` so components and tiers can be join-fetched in one query (no N+1, no
  `MultipleBagFetchException`).
- **JdbcTemplate** for the hot path: multi-row `INSERT … ON CONFLICT DO NOTHING RETURNING` (the
  dedup primitive), batched bucket upserts with `LEAST/GREATEST` and a `CASE` for last-value
  semantics, aggregate queries for replay, and the `SKIP LOCKED` work queues of the outbox and
  webhook dispatcher.
- No Hibernate schema generation anywhere: Flyway owns the schema; Hibernate validates it at startup.

## Consequences

- Each module uses the tool that fits; the boundary is visible (entities vs. `*Store` classes).
- The JDBC code is more verbose but is where performance work happens; it is covered by integration
  tests against real PostgreSQL.
- Catalog views exposed to other modules are immutable records, never entities, so lazy-loading
  never leaks across module boundaries (`open-in-view=false`).
