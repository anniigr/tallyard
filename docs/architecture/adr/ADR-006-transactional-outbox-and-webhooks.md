# ADR-006: Hand-rolled transactional outbox and durable webhook deliveries

**Status:** Accepted (2026-10-23)

## Context

Downstream systems (invoicing, CRM, notifications) must learn about rated periods and drafted
invoices reliably. Publishing to Kafka from inside a database transaction is the classic dual-write
problem: the commit may fail after the publish, or vice versa.

## Decision

- Domain modules append rows to `outbox_event` **inside their own transaction** (`Propagation.MANDATORY`
  makes a non-transactional call fail loudly).
- `OutboxPublisher` (worker role) polls with `FOR UPDATE SKIP LOCKED`, publishes to
  `billing.events.v1` keyed by aggregate id (ordering per aggregate), waits for acks, and marks rows
  published in the same transaction. Failures back off exponentially per row; nothing is dropped.
- Consumers deduplicate on the outbox event id: `webhook_delivery` has `UNIQUE (endpoint_id,
  outbox_event_id)`, so a redelivered Kafka record creates no second delivery.
- Webhook dispatch is a separate durable step: signed (`t=…,v1=HMAC-SHA256`), retried with
  exponential backoff, dead-lettered after `max-attempts`, circuit-broken per endpoint, and manually
  re-queueable. Secrets are stored AES-GCM encrypted.

Spring Modulith's event publication registry was evaluated: it provides the same pattern for Spring
application events. The explicit table was preferred so that the guarantee boundary is visible in
the schema and the publisher's behaviour under failure is one readable class.

## Consequences

- At-least-once publication; duplicates are possible after a crash between Kafka ack and commit and
  are harmless because consumers are idempotent.
- The outbox backlog gauge is an SLO signal: a growing backlog means Kafka or the publisher is down.
- Webhook endpoints that return 2xx slowly or flap are isolated by the breaker; the dashboard shows
  dead deliveries with their last error for operators.
