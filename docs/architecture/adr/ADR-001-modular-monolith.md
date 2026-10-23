# ADR-001: Modular monolith with two runtime roles instead of microservices

**Status:** Accepted (2026-10-23)

## Context

Tallyard has eleven bounded capabilities (tenancy, catalog, ingestion, metering, billing, credits,
entitlements, outbox, webhooks, dashboard, demo). Several of them must change state *together*:

- counting an event must happen in the same transaction as the check that its billing period still
  accepts usage;
- rating a period must atomically read quantities, write the invoice, consume credits, change the
  period state and enqueue outbox events;
- late events must see the exact period state at that instant.

The market research (see `docs/research`) shows that microservices are the expected vocabulary,
which creates pressure to split the system to look senior.

## Decision

Build a **modular monolith**: one Spring Boot artifact, one PostgreSQL database, strict module
boundaries verified by Spring Modulith, and **two runtime roles** selected by `TALLYARD_ROLE`:

- `api` — HTTP endpoints only (ingest, catalog, billing commands, entitlements, dashboard);
- `worker` — Kafka consumers, billing scheduler, outbox publisher, webhook dispatcher;
- `all` — both (development, tests).

Kafka sits between ingestion and metering, and between billing and webhooks, because those are the
two places where asynchronous, replayable, backpressured communication is genuinely required.

## Consequences

Positive:

- Every invariant that matters is enforced by a local transaction and a database constraint, not a
  saga. Correctness proofs are short.
- API nodes and workers scale independently (the only two scaling profiles that exist: request
  latency vs. consumer throughput).
- Module boundaries are as explicit as service boundaries (public API package + `internal`), and a
  failing `ModularityTest` is cheaper than a network hop.
- One `docker compose up`; one trace per request through every module.

Negative / accepted:

- A single PostgreSQL primary is the scaling ceiling. Table partitioning of `usage_event` and
  `meter_bucket`, then sharding by tenant, are the documented next steps (see `failure-model.md`).
- Modules cannot be deployed on different release cadences. Not a requirement today.

## Alternatives considered

- **Microservices per module**: rejected; would need an orchestration saga for rating and a
  cross-service dedup store, i.e. more code for weaker guarantees.
- **Single unstructured monolith**: rejected; the verified module boundaries are what make the
  codebase navigable and keep the dependency inversion (`billing.UsageSource` implemented by
  `metering`) honest.
