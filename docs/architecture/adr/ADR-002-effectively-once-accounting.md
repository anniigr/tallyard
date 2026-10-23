# ADR-002: Effectively-once accounting via at-least-once delivery and idempotent transactional writes

**Status:** Accepted (2026-10-23)

## Context

Usage events arrive with retries (client SDKs), are stored in Kafka (at-least-once consumption), and
are processed by workers that can crash at any point. The product requirement is that no event is
ever counted twice and no accepted event is ever lost.

"Exactly-once" is frequently claimed and rarely true end to end. Kafka transactions give exactly-once
*within Kafka* (consume-transform-produce); they say nothing about a PostgreSQL side effect.

## Decision

1. **Identity**: every event carries a client-generated `eventId`; `(tenant_id, event_id)` is a
   unique key on `usage_event`.
2. **Ingestion** publishes to Kafka with `acks=all` and an idempotent producer, and only returns 202
   after broker acknowledgement. A 503 tells the client to retry *with the same ids*.
3. **Consumption** is a batch listener with manual acknowledgement. One database transaction per
   batch does: `INSERT … ON CONFLICT (tenant_id, event_id) DO NOTHING RETURNING …` (only returned
   rows are counted), bucket upserts, adjustments, rejections. Offsets are acknowledged **after**
   commit.
4. **Crash windows**:
   - before commit → nothing visible, batch redelivered, processed again;
   - after commit, before ack → batch redelivered, every row conflicts, nothing changes
     (`ConsumerRedeliveryIT` injects exactly this failure);
   - after ack → nothing to redo.
5. **Infrastructure failure** (database down) propagates to the error handler, which retries the
   whole batch with exponential backoff forever; no batch is ever dead-lettered as a whole. Only
   individually unparseable records go to the dead-letter topic.

## Guarantee statement (what we claim, precisely)

> Every accepted event is counted **exactly once in the database** regardless of client retries,
> Kafka redelivery, consumer crashes or rebalances, provided clients use stable event ids.
> Delivery is at-least-once; the write is idempotent; the composition is effectively-once.

What is **not** claimed: exactly-once Kafka semantics, deduplication across tenants, or
deduplication of events whose client forgot to reuse the id on retry.

## Consequences

- Deduplication cost is one unique index probe per event, inside the same transaction as the count —
  no external dedup store, no window to keep consistent.
- The dedup window equals the retention of `usage_event` (unbounded by default), unlike stream-side
  windows (OpenMeter: 32 days) which silently accept re-sent events after the window.
- The event log doubles as the audit and replay source (ADR-004).
