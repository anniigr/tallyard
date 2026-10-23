# Tallyard — Use Cases and User Journeys

*2026-10-23.*

## Actors

- **Integrator** — engineer wiring a product's usage into Tallyard (uses `INGEST`/`ADMIN` keys).
- **Billing operator** — closes periods, inspects invoices, resolves late/rejected events (`ADMIN`).
- **Product backend** — calls the entitlement endpoint before serving a request (`READ`).
- **Downstream system** — receives webhooks (`invoice.drafted`, `period.rated`, `credit.depleted`).
- **SRE** — watches dashboards, acts on runbooks.

## UC-1 Onboard a tenant and define pricing

1. Operator creates a tenant and receives an `ADMIN` key (shown once).
2. Operator creates meters: `api_calls` (COUNT of `request`), `tokens` (SUM of `request.tokens`),
   `active_devices` (UNIQUE_COUNT of `heartbeat.device_id`).
3. Operator creates plan `pro` (USD): `api_calls` GRADUATED (0–100k free, then $0.50/1k, then
   $0.30/1k), `tokens` PER_UNIT $0.000002 with 1M included, `active_devices` PACKAGE 10 for $5.
   Minimum commitment $20.
4. Operator creates customer `acme` and subscribes it to `pro` from now; Tallyard opens the first
   `BillingPeriod`.

**Outcome:** catalog ready; first period `OPEN`.

## UC-2 Ingest usage (happy path)

1. Product backend posts a batch of 200 events with client-generated `eventId`s.
2. Tallyard validates each event, publishes accepted ones to Kafka keyed by `tenant:subject`,
   returns `202` with per-event status.
3. Worker consumes, inserts into the event log (dedup), updates hourly buckets, acks.
4. Within one second the dashboard shows the new totals.

## UC-3 Client retry storm (duplicates)

1. The product backend times out and re-sends the same batch three times.
2. Every event beyond the first copy hits the unique `(tenantId, eventId)` constraint and is counted
   as a duplicate in metrics; totals do not change.
3. The `duplicates` counter on the dashboard rises; the `usage` counter does not.

## UC-4 Worker crash and recovery

1. Operator runs `docker compose kill worker` while the simulator streams 1,000 events/s.
2. Ingest keeps returning `202` (events buffer in Kafka); consumer lag grows on the dashboard.
3. Operator restarts the worker; the batch in flight at crash time is redelivered.
4. Rows already committed are deduplicated; lag drains; the simulator's final truth check shows
   expected totals == stored totals.

## UC-5 Late and out-of-order events

1. An offline SDK flushes events with `occurredAt` from two days ago.
2. If the target period is still `OPEN`/`CLOSING`, they are applied to it.
3. If the target period is already `RATED`, they are stored as adjustments on the current period;
   the dashboard lists them under "Adjustments" with the original period reference.
4. Events older than the meter's acceptance window are rejected with
   `OUTSIDE_ACCEPTANCE_WINDOW` and listed under "Rejected".

## UC-6 Close and rate a period

1. The scheduler moves the period to `CLOSING` at period end and to `RATED` after the grace window
   (operator may force it: `POST /v1/periods/{id}/rate`).
2. Rating computes quantities from buckets (and distinct values for UNIQUE_COUNT), applies plan
   components, minimum commitment and credits, and stores lines with tier-by-tier breakdowns and a
   reproducibility hash.
3. A `period.rated` + `invoice.drafted` event is written to the outbox and published; webhooks fire.
4. The next period is already `OPEN`; usage continues uninterrupted.

## UC-7 Prove reproducibility by replay

1. Auditor asks "why is this line $137.50?".
2. Operator opens the line: the breakdown shows each tier, quantity and amount.
3. Operator triggers `POST /v1/periods/{id}/replay` — Tallyard recomputes quantities from the raw
   event log and re-rates in dry-run mode.
4. The response states `matches: true` with identical hash (or lists the differences).

## UC-8 Entitlement enforcement

1. Product backend calls `POST /v1/entitlements/check` with `{subject, meter, requested: 1}`.
2. Tallyard answers `ALLOW`/`DENY` with `remaining`, `limit`, `asOf`.
3. If the customer runs out of prepaid credit, subsequent checks deny; a `credit.depleted` event is
   published.

## UC-9 Webhook delivery failure

1. The tenant's endpoint returns 500.
2. Deliveries are retried with exponential backoff; after the configured number of attempts the
   delivery is dead-lettered and shown in the dashboard with the last response.
3. Operator fixes the endpoint and requests redelivery.

## UC-10 Kafka outage

1. Kafka is stopped (`docker compose stop kafka`).
2. Ingest returns `503` with `Retry-After`; readiness reports Kafka down; everything else (catalog,
   usage queries, entitlement checks, rating) continues.
3. Kafka returns; ingest resumes; the simulator's retries carry the same `eventId`s, so nothing is
   double-counted.

## Demo script (10 minutes)

| Minute | Action | Visible outcome |
|---|---|---|
| 0–1 | `docker compose up`; open dashboard | Seeded tenant, plan, 20 customers; period OPEN |
| 1–3 | Start simulator at 500 ev/s with 5 % duplicates, 2 % late, 1 % invalid | Usage climbs; duplicate/late/rejected counters move; traces visible in Jaeger |
| 3–4 | Entitlement checks against a capped customer | ALLOW turns to DENY at the cap |
| 4–6 | `docker compose kill worker`; restart | Lag grows then drains; totals identical to simulator's truth |
| 6–8 | Force-close and rate the period | Invoice drafts with breakdowns; webhook delivered (echo endpoint) |
| 8–9 | Replay the period | `matches: true`, identical hash |
| 9–10 | Grafana: ingest p99, dedup rate, consumer lag over the whole session | Measured, not asserted |
