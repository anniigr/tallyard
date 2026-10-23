# Tallyard — Product Specification

*Phase 4 deliverable. Version 1.0, 2026-10-23.*

## 1. Identity

| | |
|---|---|
| **Name** | Tallyard |
| **Tagline** | Billing-grade usage metering that counts exactly once. |
| **One-sentence pitch** | Tallyard ingests high-volume usage events from software products, counts them exactly once despite retries, crashes and late arrivals, rates them against pricing plans, enforces real-time entitlements, and produces auditable invoice lines that can be reproduced by replay. |
| **Category** | Financial transaction infrastructure / developer infrastructure |
| **Form** | Self-hostable backend service (REST API + Kafka) with an operational dashboard |

## 2. Problem statement

Any product that charges per unit of consumption — API calls, tokens, gigabytes, messages, seats, build
minutes — must answer three questions correctly and continuously:

1. **How much did customer X use in period P?** The answer must be the same no matter how many times
   an event was retried, how late it arrived, or how often the processing service crashed.
2. **What does that usage cost under X's plan?** The answer must be deterministic, auditable and
   reproducible months later.
3. **May X consume one more unit right now?** The answer must come back in milliseconds and must not
   drift far from the ledger.

Teams typically start with a `usage` table and a cron job, then discover duplicates from client
retries, late events from offline SDKs, mid-period plan changes, negative prepaid balances under
concurrent requests, and invoices that cannot be explained. Commercial products (Stripe Billing
Meters, Orb, Metronome) and open-source projects in Go/Ruby (OpenMeter, Lago) solve this; there is no
modern Java/Spring open-source equivalent.

## 3. Target users

| User | Needs |
|---|---|
| **Product/platform engineer** integrating a SaaS product | Simple, idempotent ingest API; SDK-friendly batch endpoint; clear error semantics |
| **Billing operations** | Visibility into usage per customer, period status, draft invoice lines, late/rejected events, ability to close/re-rate/replay a period |
| **Finance / audit** | Deterministic, explainable line items with tier-by-tier breakdowns; immutable event log; reproducibility proof |
| **SRE / on-call** | Health, consumer lag, dedup/late rates, dead letters, webhook failures, runbooks |

## 4. Domain model

### 4.1 Entities

| Entity | Description | Key rules |
|---|---|---|
| **Tenant** | An organisation using Tallyard | Isolation boundary for all data; owns API keys |
| **ApiKey** | Credential for a tenant | Stored as SHA-256 hash + public prefix; scopes `INGEST`, `READ`, `ADMIN`; revocable |
| **Meter** | A named thing to count (`api_calls`, `tokens_in`) | `code` unique per tenant; `eventType` selects matching events; `aggregation` ∈ {SUM, COUNT, MIN, MAX, LAST, UNIQUE_COUNT}; `valueProperty` names the numeric/string property to aggregate; `acceptanceWindow` bounds how old an event may be |
| **Customer** | The subject whose usage is counted | `externalId` unique per tenant (the `subject` field in events) |
| **Plan** | Pricing definition in one currency | Contains ordered `PlanComponent`s; immutable once any subscription references it (new version instead) |
| **PlanComponent** | Price of one meter under a plan | `pricingModel` ∈ {FLAT, PER_UNIT, GRADUATED, VOLUME, PACKAGE}; `includedUnits`; ordered `PriceTier`s (`upTo` nullable = ∞, `unitPrice`, `flatAmount`); `packageSize`; optional `hardLimit` for entitlements |
| **Subscription** | Customer ↔ Plan over time | `startsAt`, optional `endsAt`; exactly one active per customer at any instant; `billingAnchor` day |
| **BillingPeriod** | One interval of a subscription | States `OPEN → CLOSING → RATED → FINALIZED`; `graceUntil` for late events; optimistic version |
| **UsageEvent** | Immutable record of one ingested event | `(tenantId, eventId)` unique — the deduplication key; `occurredAt` (event time), `ingestedAt`, `subject`, `eventType`, `value`, `properties` JSONB |
| **MeterBucket** | Hourly aggregate per (meter, customer) | Incrementally updated; the real-time read model |
| **UsageAdjustment** | A late event applied to a later period | References the original event and the period it *would* have belonged to |
| **RejectedEvent** | Event refused with a reason | Reasons: `OUTSIDE_ACCEPTANCE_WINDOW`, `UNKNOWN_SUBJECT`, `NO_ACTIVE_SUBSCRIPTION`, `SCHEMA_INVALID`, `NO_MATCHING_METER` |
| **InvoiceDraft / InvoiceLine** | Output of rating a period | Line per plan component with `quantity`, `amount`, `breakdown` (tier-by-tier JSON), credits applied, `reproducibilityHash` |
| **CreditGrant** | Prepaid monetary credit for a customer | `amountGranted`, `amountConsumed`, `expiresAt`, priority; `amountConsumed ≤ amountGranted` always |
| **CreditLedgerEntry** | Immutable record of credit consumption | Links grant → invoice line |
| **OutboxEvent** | Domain event awaiting publication | Written in the same transaction as the state change |
| **WebhookEndpoint / WebhookDelivery** | Tenant HTTP endpoints and delivery attempts | HMAC-SHA256 signature; exponential backoff; dead-letter after N attempts |

### 4.2 Business rules

**Counting**

- R1. An event is identified by `(tenantId, eventId)`. A second arrival with the same identity is a
  duplicate and has no effect, regardless of payload differences (first write wins).
- R2. An event matches a meter when `event.type == meter.eventType`. One event may match several
  meters (e.g. `COUNT` of requests and `SUM` of tokens).
- R3. Event time is `occurredAt` supplied by the client; `ingestedAt` is assigned by Tallyard. All
  period arithmetic uses `occurredAt`.
- R4. An event older than `now − meter.acceptanceWindow` or more than 5 minutes in the future is
  rejected (`OUTSIDE_ACCEPTANCE_WINDOW`).
- R5. An event whose subject has no active subscription at `occurredAt` is rejected
  (`NO_ACTIVE_SUBSCRIPTION`).

**Periods and late events**

- R6. Each subscription has at most one `OPEN` period at a time; periods are contiguous and
  non-overlapping.
- R7. A period transitions `OPEN → CLOSING` when `now ≥ period.end`; `CLOSING → RATED` when
  `now ≥ graceUntil` (or on explicit operator command); `RATED → FINALIZED` on operator command or
  automatically after a configurable delay. Transitions are irreversible.
- R8. Late-event policy: if the target period is `OPEN` or `CLOSING`, the event is applied to it. If
  the target period is `RATED` or `FINALIZED` and the event is within the acceptance window, it is
  recorded as a `UsageAdjustment` against the subscription's current `OPEN` period and appears as a
  separate adjustment line when that period is rated. If outside the window it is rejected (R4).
- R9. Rating is a pure function of (plan, aggregated quantities, adjustments, credit state). Rating
  the same inputs twice yields identical lines and the same `reproducibilityHash`.

**Pricing models** (all amounts exact decimals; currency rounding HALF_EVEN at line level)

- FLAT: fixed amount per period regardless of quantity.
- PER_UNIT: `max(0, quantity − includedUnits) × unitPrice`.
- GRADUATED: each tier prices only the units within its band (Stripe/Lago "graduated").
- VOLUME: the tier containing the total quantity prices *all* billable units.
- PACKAGE: `ceil(billable / packageSize) × packagePrice`.
- Minimum commitment: if the sum of component amounts is below `plan.minimumAmount`, a
  `MINIMUM_COMMITMENT` line tops it up.

**Credits**

- R10. At rating, credits are applied to the invoice total in order of `expiresAt` then `priority`,
  never exceeding remaining balance; `amountConsumed` never exceeds `amountGranted` (enforced by DB
  constraint and row lock).
- R11. Credit consumption is recorded as immutable ledger entries.

**Entitlements**

- R12. `check(subject, meter, requested)` returns ALLOW if `currentPeriodUsage + requested ≤ hardLimit`
  for the subscription's component; if no `hardLimit`, ALLOW. If the customer has credit grants, the
  check also requires `estimatedRemainingCredit > 0`, where estimate = balance − live rating of the
  current period.
- R13. The entitlement answer may lag the ledger by at most the configured staleness bound
  (default 2 s), which is documented and measured.

**Publication**

- R14. Every period transition, invoice draft and credit depletion emits a domain event through the
  transactional outbox; at-least-once publication; consumers must deduplicate on `eventId`.
- R15. Webhook deliveries are retried with exponential backoff up to N attempts, then dead-lettered
  and visible to operators.

## 5. Functional requirements

| ID | Requirement | Priority |
|---|---|---|
| F1 | Create/list meters, customers, plans, subscriptions, credit grants, webhook endpoints via REST (ADMIN) | Essential |
| F2 | Ingest a single event or a batch (≤ 1000) via REST (INGEST), returning per-event accepted/invalid status | Essential |
| F3 | Process events asynchronously via Kafka with per-customer ordering and effectively-once accounting | Essential |
| F4 | Expose live usage per customer/meter for the current period (READ) | Essential |
| F5 | Automatically open, close and rate billing periods; manual close/rate/finalize endpoints | Essential |
| F6 | Produce invoice drafts with tier-by-tier breakdown and credit application | Essential |
| F7 | Replay a period from the event log and report whether the result matches the stored draft | Essential |
| F8 | Entitlement check endpoint with p99 latency target | Essential |
| F9 | List late (adjusted), rejected and dead-lettered events with reasons | Essential |
| F10 | Publish domain events to Kafka via outbox and deliver webhooks with retry/dead-letter | Essential |
| F11 | Operational dashboard showing F4, F5, F6, F9, F10 live | Demonstration |
| F12 | Simulator producing realistic traffic with configurable duplicate/late/invalid ratios and a final truth check | Demonstration |
| F13 | OpenAPI 3.1 contract published and verified by tests | Essential |

## 6. Non-functional requirements

| Area | Requirement |
|---|---|
| Correctness | No double counting under duplicate delivery, consumer restart or rebalance; invariants enforced by DB constraints as well as code |
| Throughput | ≥ 2,000 events/s sustained ingest on a laptop (8-core M3, Docker) with p99 ingest latency < 100 ms at that load; measured, not assumed |
| Latency | Entitlement check p99 < 20 ms at 500 req/s (warm cache) |
| Availability | Kafka outage → ingest returns 503 with `Retry-After`; everything else keeps working. PostgreSQL outage → consumers pause and resume without data loss |
| Security | API-key auth with scopes; keys hashed; tenant isolation on every query; management port not exposed; no secrets in the repo |
| Observability | Structured logs with trace/tenant correlation; Prometheus metrics; OpenTelemetry traces across HTTP → Kafka → DB; Grafana dashboard with real panels |
| Operability | One-command local start; health/readiness/liveness; runbooks |
| Maintainability | Verified module boundaries (Spring Modulith); ArchUnit rules; ≥ 80 % line coverage on domain packages (measured) |

## 7. Constraints and assumptions

- Single region, single PostgreSQL primary; no multi-region deduplication.
- Event time is trusted from the client (as Stripe does); clock skew bounded by R4.
- Currency per plan; no FX. Amounts stored with scale 6, presented rounded to the currency's minor
  unit.
- Billing interval: monthly (anchor day) and, for demonstrations, configurable short intervals
  (e.g. 5 minutes) so a full lifecycle fits in a demo.

## 8. Failure scenarios the product must handle

| Scenario | Expected behaviour |
|---|---|
| Client retries a batch | Duplicates ignored; response reports `duplicate` status on re-submission |
| Worker crashes after DB commit, before offset commit | Redelivered batch is fully deduplicated; totals unchanged |
| Worker crashes mid-transaction | Transaction rolls back; batch reprocessed |
| Two workers rebalance partitions | Per-subject ordering preserved within a partition; no double counting |
| Kafka unavailable | Ingest returns 503 + `Retry-After`; health shows degraded |
| PostgreSQL unavailable | Consumer retries with backoff; no acks; resumes automatically |
| Invalid payload | Rejected with reason; published to dead-letter topic; visible in dashboard |
| Event for a rated period | Recorded as adjustment in current period |
| Event older than window | Rejected `OUTSIDE_ACCEPTANCE_WINDOW` |
| Two schedulers attempt to rate the same period | Optimistic lock + `FOR UPDATE SKIP LOCKED`; exactly one succeeds |
| Webhook endpoint down | Retries with backoff; circuit opens; dead-letter after N attempts |
| Concurrent credit consumption | Row lock + check constraint; balance never negative |

## 9. Security requirements

- API-key authentication on all `/v1/**` endpoints; scopes enforced per endpoint.
- Keys shown once at creation; only hash and prefix stored.
- Tenant id derived from the key, never from the request body.
- Input validation (Bean Validation + JSON schema for event properties size/depth).
- Rate limiting per key on ingest.
- Webhook payloads signed with per-endpoint HMAC secret.
- Actuator on a separate management port; Prometheus scrape only inside the Compose network.

## 10. Operational requirements

- `docker compose up` starts the full stack with seed data and a demo tenant.
- Health: liveness, readiness (DB + Kafka), `info` with build metadata.
- Metrics: ingest rate, dedup rate, late rate, rejected rate, batch processing time, consumer lag,
  rating duration, outbox backlog, webhook outcomes.
- Runbooks for: consumer lag growth, dead letters, stuck period, webhook failures, Kafka/DB outage.

## 11. Scope classification

| Essential domain functionality | Advanced technical capabilities | Operational infrastructure | Optional extensions (roadmap) |
|---|---|---|---|
| Meters, customers, plans, subscriptions | Idempotent consumer + DB dedup | Docker Compose stack | Monthly table partitioning |
| Ingest API + Kafka pipeline | Period state machine with late-event policy | Prometheus/Grafana/Jaeger | Multi-currency |
| Aggregation + live usage | Deterministic rating + replay proof | CI with Testcontainers | Multi-region dedup |
| Rating + invoice drafts | Credit ledger under concurrency | Runbooks | Payment provider sync |
| Entitlement check | Outbox + webhooks with DLQ | Load tests (k6) | Deterministic anomaly detection |
