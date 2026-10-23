# Final Project Selection: Tallyard

*Decision date: 2026-10-23.*

## Decision

**Tallyard** — a billing-grade usage metering, rating and entitlement engine — is the selected concept.

One-sentence pitch: *Tallyard ingests high-volume usage events from software products, counts them
exactly once despite retries, crashes and late arrivals, rates them against pricing plans, enforces
real-time entitlements, and produces auditable invoice lines that can be reproduced by replay.*

## Why this concept

1. **The hard problems are unavoidable, not decorative.** A metering engine that double-counts, loses
   late events, or lets a prepaid balance go negative is simply broken. Idempotent consumption,
   ordering, atomic period closing, deterministic rating and concurrency control are therefore part
   of the product definition. This satisfies Rule 1 (no technology theatre) and Rule 7 (no irrelevant
   complexity) by construction.
2. **It maps onto the target market.** The Warsaw/EU Java market is dominated by fintech, banking,
   insurance and SaaS employers; "metering → money" is their language. The stack (Spring Boot, JPA,
   Kafka, PostgreSQL, Testcontainers) is exactly what postings ask for (see
   [job-market-analysis.md](job-market-analysis.md)).
3. **It is differentiated.** The commercial category exists (Stripe Meters, OpenMeter, Lago, Orb,
   Metronome), which proves the problem is real, but there is no modern open-source Java
   implementation. A reviewer remembers "the metering engine that survives duplicate and late events
   and replays invoices byte-for-byte".
4. **It demonstrates well.** Live meters move, a worker is killed and restarted, totals stay correct,
   a period closes, an invoice draft appears, and a replay reproduces it. Every step is observable in
   metrics and traces.
5. **It is measurable.** Ingestion throughput and p99, entitlement-check latency, consumer lag and
   rating duration are natural benchmarks with honest targets.
6. **It is useful beyond the portfolio.** A small SaaS or an internal platform team could run it.

## What is explicitly in scope (essential domain functionality)

- Multi-tenant model: tenant → meters, customers (subjects), plans, subscriptions.
- Event ingestion API (single and batch) with idempotency keys and schema validation.
- Kafka-based asynchronous processing with per-customer ordering.
- Idempotent consumer with database-backed deduplication and bounded acceptance window.
- Meter aggregation (SUM, COUNT, MAX, UNIQUE_COUNT, LAST) into hourly buckets per customer.
- Billing periods with an explicit lifecycle (OPEN → CLOSING → RATED → FINALISED) and a late-event
  policy (accept while open, adjust into the next period after close, reject beyond horizon).
- Rating engine: flat, per-unit, graduated tiers, volume tiers, package pricing, included quantities,
  minimum commitments, prepaid credit drawdown — exact decimal arithmetic with explicit rounding.
- Prepaid credit ledger with non-negative invariant under concurrency.
- Real-time entitlement check (allow/deny with remaining quota) with documented staleness bound.
- Transactional outbox → `billing.events` topic → webhook delivery with retry and dead-lettering.
- Deterministic replay of a period from the immutable event log.
- Operational dashboard (demonstration layer) and a load/chaos simulator.

## What is explicitly out of scope

- Payment collection, tax calculation, PDF invoices, currency conversion.
- Multi-region active-active deduplication.
- A general-purpose rules engine or scripting for pricing.
- LLM-based features (see the AI assessment in the architecture documentation: the system is
  stronger without an unavailable paid dependency on its core path; deterministic anomaly detection
  is the appropriate tool if such a feature is added later).

## Risks accepted and their mitigations

| Risk | Mitigation |
|---|---|
| Rating-rule sprawl | Fixed, documented pricing model set; everything else is a roadmap item |
| Entitlement hot-path vs. ledger consistency | Explicitly bounded staleness; documented, tested, measured |
| "Exactly-once" over-claiming | Claim *effectively-once accounting* via at-least-once delivery + idempotent, transactional writes; document the guarantee boundary |
| Period-close race conditions | Single-writer close transaction with state checks; late events after close become adjustments |
| Scope creep into a full billing suite | Invoices stop at "draft line items"; no payments |

## Name check

GitHub search (2026-10-23) for `tallyard in:name` returned five unrelated zero-star repositories
(TypeScript/C#/HTML) and no Java project; `anniigr/tallyard` does not exist. The name is distinctive
enough and available in this account.

## Next steps

Phase 4 (product specification, use cases, acceptance criteria) and Phase 5 (architecture, ADRs,
technology decisions, failure model) follow immediately, then implementation.
