# Failure Model and Guarantees

*What Tallyard promises, what it does not, and what happens when each component fails. Every row
names the mechanism in code and the test or procedure that exercises it.*

## 1. Guarantees

| Guarantee | Mechanism | Verified by |
|---|---|---|
| An accepted event is counted exactly once in the database, under client retries, Kafka redelivery, consumer crashes and rebalances (given stable `eventId`s) | `usage_event (tenant_id, event_id)` unique; `INSERT … ON CONFLICT DO NOTHING RETURNING`; one transaction per batch; ack after commit | `MeteringPipelineIT`, `ConsumerRedeliveryIT`, simulator truth check |
| 202 means durable | Producer `acks=all`, idempotent, awaited before responding | `IngestApiIT`, Kafka-outage procedure |
| Usage is never written into a settled period | `FOR SHARE` (metering) vs `FOR UPDATE` (rating) on the period row; state check inside the metering transaction | `BillingLifecycleIT.lateEvent…`, ADR-003 |
| Exactly one invoice per period | `FOR UPDATE SKIP LOCKED` claim + `@Version` + `invoice_draft.period_id UNIQUE` | `BillingLifecycleIT.concurrentRating…` |
| Invoices are reproducible from raw events | Pure `RatingEngine`; event log read as-of `rated_at`; hash comparison | `RatingEngineTest`, `RatingEnginePropertyTest`, `BillingLifecycleIT` replay assertions |
| Prepaid balance never negative | Grants locked `FOR UPDATE` in deterministic order; `CHECK (amount_consumed <= amount_granted)` | `CreditLedgerConcurrencyIT` |
| Domain events are published at least once, in order per aggregate, only for committed transactions | Outbox table written in-transaction (`MANDATORY`), `SKIP LOCKED` publisher, ack-then-mark | `OutboxPublicationIT` |
| Webhooks: at-most-one delivery row per (endpoint, event); retries with backoff; dead-letter after N; manual retry | `webhook_delivery` unique key; dispatcher; breaker | `WebhookDeliveryIT` |
| Entitlement answers are at most `cache-ttl` stale | Caffeine snapshot TTL | `EntitlementIT` |
| Tenant isolation | Tenant id derived from the key; every query filters by tenant; cross-tenant ids → 404 | `SecurityIT` |

## 2. Explicit non-guarantees

- No exactly-once Kafka semantics; duplicates on `billing.events.v1` are possible and harmless.
- No deduplication of events whose client generated a *new* id on retry.
- No read-your-writes for entitlements (bounded staleness instead).
- No multi-region or multi-primary operation.
- `UNIQUE_COUNT` meters cannot absorb late events (rejected with a reason).

## 3. Component failure matrix

| Failure | Detection | Behaviour | Recovery | Data at risk |
|---|---|---|---|---|
| Worker process dies | Consumer lag, `tallyard.metering.batches` stops, readiness of that node | Ingest unaffected; counting pauses; periods not rated; webhooks pause | Restart (Compose `restart` or orchestrator); redelivered batches are no-ops | None |
| Worker dies mid-transaction | Same | Transaction rolled back by PostgreSQL | Redelivery reprocesses the batch | None |
| Worker dies after commit, before ack | Same | Redelivery hits unique key | Automatic | None (tested) |
| Two workers rebalance | Partition revocation logs | In-flight batch may be reprocessed by the new owner → unique key | Automatic | None |
| Kafka unavailable | `kafka` health DOWN; `tallyard.ingest.publish` timeouts; 503 rate | Ingest returns 503 + `Retry-After: 5`; consumers reconnect with backoff; outbox publisher backs off, backlog gauge grows; everything else works | Kafka returns; producers/consumers reconnect; outbox drains | None (clients retry with same ids) |
| PostgreSQL unavailable | `db` health DOWN; HTTP 5xx; consumer error handler logs | API DB-backed endpoints fail (5xx); consumer batch retries with exponential backoff (cap 30 s) forever, no acks; schedulers log and skip | Automatic when DB returns | None |
| PostgreSQL slow / lock waits | Rating timer p99, Hikari pending connections | Rating waits for in-flight metering batches (bounded by batch time) | – | None |
| Poison Kafka record | `tallyard.metering.rejected{reason=SCHEMA_INVALID}`; DLT size | Record forwarded to `usage.events.v1.dlt` with reason/source headers; rejection row; batch continues | Operator inspects DLT; fix producer | The poison record only |
| Unknown subject / meter / no subscription | `rejected` by reason; dashboard | Event kept in log, rejection recorded; not counted | Create the customer/meter and replay the period if needed | Revenue for misconfigured subjects (visible) |
| Event older than acceptance window | `OUTSIDE_ACCEPTANCE_WINDOW` | Rejected; kept in log | Policy decision | By design |
| Event for a rated period | `tallyard.metering.events{outcome=late}` | Adjustment on the open period; priced marginally at next rating | – | None |
| Scheduler races (two workers) | `lost a race` info log | `SKIP LOCKED` prevents most; `@Version` rejects the rest with 409 | Next tick | None |
| Webhook endpoint down | `tallyard.webhooks.deliveries{outcome=failed}`, pending gauge, breaker open | Backoff `5 s × 2^attempt`, dead after 8 attempts (configurable); breaker pushes due deliveries back while open | Fix endpoint; `POST /v1/webhooks/deliveries/{id}/retry` | None (payload persisted) |
| Encryption key missing | Startup OK; registering a webhook fails 409 `webhooks.encryption_key_missing` | Clear operator error instead of plaintext secrets | Set `TALLYARD_ENCRYPTION_KEY` | None |
| API key leaked | Key prefix in logs/MDC | Revoke via `DELETE /v1/api-keys/{id}`; cache TTL 30 s bounds residual validity | Issue new key | Until revocation |
| Clock skew on clients | `occurredAt` > now + 5 min → INVALID at ingest | Rejected per item | Client fixes clock | None |

## 4. Capacity and scaling path

1. **Today** (laptop, Compose): one API, one worker, 6 partitions. Measured numbers in
   `docs/performance`.
2. **More ingest**: add API nodes (stateless) behind a load balancer; raise partitions (≥ expected
   worker count × concurrency).
3. **More counting**: add workers up to the partition count; hot customers are bounded by one
   partition each — the same property that gives ordering.
4. **PostgreSQL**: `usage_event` monthly range partitioning (pg_partman) with the unique key per
   partition and a tenant-scoped retention policy; `meter_bucket` partitioned by `period_id` hash;
   read replica for dashboards and replay.
5. **Beyond one primary**: shard by tenant (every table carries `tenant_id`; no cross-tenant
   transactions exist), which is also the natural multi-region story.

## 5. Consistency summary

| Boundary | Model |
|---|---|
| Within one API request | Transactional (PostgreSQL) |
| Ingest → counted | Eventually consistent; typical lag tens to hundreds of milliseconds (consumer poll + batch) |
| Counted → entitlement decision | Bounded staleness = `cache-ttl` (2 s default) |
| Rating → webhook | At-least-once, seconds; ordered per aggregate |
| Invoice ↔ raw events | Exact, provable by replay |
