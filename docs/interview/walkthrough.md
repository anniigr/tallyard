# Interview Walkthrough

*Phase 14 deliverable. Scripts at increasing depth. Every claim points at code
(`tallyard-app/src/main/java/io/tallyard/…`), tests or an ADR.*

## 30-second pitch

> Tallyard is a usage metering and rating engine — the part of a SaaS stack that turns raw usage
> events into invoices. The hard part is doing that correctly: clients retry, events arrive late,
> workers crash, and prepaid balances are consumed concurrently. Tallyard counts every event exactly
> once in the database despite all of that, rates usage deterministically, and can prove any invoice
> by replaying it from the raw event log. Java 21, Spring Boot 4, Kafka, PostgreSQL, with a verified
> modular-monolith structure and tests that run against the real infrastructure.

## Two-minute system overview

1. **Ingest** (`ingestion`): `POST /v1/events` validates up to 1,000 events, rate-limits per API key,
   publishes to Kafka with `acks=all` keyed by `tenant:subject`, and only answers 202 after the broker
   acknowledged — so 202 means durable. Kafka down → 503 with `Retry-After`.
2. **Count** (`metering`): a batch consumer processes up to 500 records in one PostgreSQL
   transaction: `INSERT … ON CONFLICT (tenant_id, event_id) DO NOTHING RETURNING` is the
   deduplication; only returned rows are folded into hourly buckets per (period, meter, customer).
   Offsets are acknowledged after commit → effectively-once accounting (ADR-002).
3. **Periods** (`billing`): `OPEN → CLOSING → RATED → FINALIZED`; metering holds `FOR SHARE` on the
   period row while writing, rating takes `FOR UPDATE`, so usage can never land in a settled period.
   Late events become adjustments on the open period (ADR-003).
4. **Rate**: a pure `RatingEngine` (graduated, volume, per-unit, package, flat, minimum commitment)
   produces lines with tier-by-tier breakdowns and a SHA-256 hash; prepaid credits are applied with
   row locks and a check constraint. `replay` recomputes from the raw log as of rating time and proves
   the stored invoice matches (ADR-004).
5. **Decide** (`entitlements`): "may this subject consume N more?" answered from a 2-second snapshot
   cache — explicit bounded staleness (ADR-005).
6. **Publish** (`outbox`, `webhooks`): transactional outbox → `billing.events.v1` → durable, signed
   webhook deliveries with backoff, dead-lettering and a circuit breaker (ADR-006).
7. **Operate**: one artifact, two roles (`api`/`worker`), Prometheus metrics, OpenTelemetry traces
   across HTTP → Kafka → JDBC, Grafana dashboard, runbooks.

## Five-minute architectural walkthrough

Open `docs/architecture/architecture.md` and follow the module diagram.

- **Why a modular monolith** (ADR-001): the invariants (dedup + period state + rating + credits)
  live in one transaction. Microservices would need sagas for weaker guarantees. Module boundaries
  are verified by `ModularityTest` — a cycle fails the build. The one apparent cycle (billing needs
  usage; metering needs period state) is broken by the `billing.UsageSource` SPI implemented in
  `metering` — dependency inversion at the package level.
- **Where JPA stops and JDBC starts** (ADR-007): aggregates with invariants (periods, invoices,
  grants) are entities with `@Version` and pessimistic locks; the hot path (`UsageEventStore`) is
  set-oriented SQL — multi-row insert-returning, batched upserts with `LEAST/GREATEST`, `SKIP LOCKED`
  work queues.
- **The two usage sources**: buckets for speed, the immutable log for proof. The SQL that recomputes
  from the log mirrors the consumer's filters (acceptance window, plain-decimal grammar) and reads
  "as of" `rated_at`, which is what makes replay meaningful after late events.
- **Failure design** (`failure-model.md`): every component failure has a detection signal, a
  behaviour, and a recovery, and the dangerous window (commit-then-crash-before-ack) has a dedicated
  test with a failure-injection hook.

## Ten-minute live demonstration

| Step | Command / click | What to point at |
|---|---|---|
| 1 | `make up`; open `http://localhost:8080`, paste the demo key | 20 customers, three plans, periods OPEN |
| 2 | `make sim RATE=500 DURATION=90` | Counters climb; duplicate/late/invalid rates in the simulator output and Grafana |
| 3 | Dashboard → "Entitlement check" for `cust-005` with 6000 | DENY `LIMIT_EXCEEDED` (starter cap 5,000) |
| 4 | `docker compose -f deploy/docker-compose.yml kill worker` | Lag grows; ingest still 202 |
| 5 | `docker compose … up -d worker` | Lag drains; simulator ends with `ALL 20 CUSTOMERS MATCH` |
| 6 | Dashboard → "rate now" on a customer | Invoice draft with tier breakdowns, credits for the prepaid customer |
| 7 | "replay" on the rated period | `✓ matches` with identical hash |
| 8 | Webhook deliveries panel | `DELIVERED`, signature `valid` in the sink |
| 9 | Jaeger → search `tallyard` | One trace: HTTP → Kafka send → consume → JDBC |
| 10 | Grafana → Operations dashboard | p99s, lag, outcomes over the whole run |

## Deep technical explanation (Senior)

**Why `ON CONFLICT DO NOTHING RETURNING` instead of a pre-check?** A `SELECT` then `INSERT` is a
race; the unique index is the only atomic deduplicator. `RETURNING` gives the exact set of new rows
in the same statement, so there is no second round trip and no window.

**Why buckets at all?** Rating and dashboards need current totals in milliseconds; scanning the
event log per request would be O(events). Buckets are an incrementally maintained materialised view
keyed by period so they never straddle periods. Their correctness is checked by replay, not assumed.

**Why `FOR SHARE` in the consumer?** Shared locks let many metering batches write into the same
period concurrently (they do not conflict with each other) but block the single rating transaction
until they commit — and block new batches while rating holds `FOR UPDATE`. This is the cheapest
protocol that gives "no usage lands in a settled period" without a global lock.

**Why batches are one transaction.** Per-event transactions would cost a commit per event (fsync);
one transaction per 500 records amortises it and keeps the ack semantics trivial: commit, then ack.
The error handler retries whole batches with exponential backoff; idempotency makes that safe.

**Money.** `BigDecimal` at scale 6 during computation, HALF_EVEN to minor units per line, subtotal
as the sum of rounded lines — matches what the customer adds up. Hash over canonical line rendering.

**Ordering.** Per-customer order comes from the partition key; aggregates are commutative except
LAST, which is decided by `occurred_at`, not arrival — so out-of-order delivery is harmless.

## Architecture defence (Staff)

See `architecture-defense.md` for the question-by-question version. The short form:

- The system is correct because every invariant is enforced where it is cheapest and strongest: in
  PostgreSQL constraints and row locks, inside one transaction. Kafka provides durability, replay and
  backpressure — not correctness.
- The hardest problem was the interaction between late events, period settlement and replay. The
  resolution — adjustments carried forward, buckets frozen, replay as-of rating time — keeps invoices
  immutable and still provable.
- The known ceiling is a single PostgreSQL primary. The path (partitioning → tenant sharding) is
  possible because no transaction ever spans tenants.
