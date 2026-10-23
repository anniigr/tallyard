# Operations Runbooks

*Phase 9 deliverable. Each runbook states the signal, how to confirm, what to do, and how to verify
recovery. Metric names are the ones exposed at `/actuator/prometheus`; panel names refer to the
provisioned Grafana dashboard "Tallyard — Operations".*

## Quick reference

| Signal | Metric / endpoint | Runbook |
|---|---|---|
| Counting stopped or slow | `kafka_consumer_fetch_manager_records_lag_max` rising; `tallyard_metering_batches_total` flat | [R1](#r1-consumer-lag-is-growing) |
| Clients get 503 on ingest | `http_server_requests_seconds_count{status="503"}`; readiness `kafka: DOWN` | [R2](#r2-kafka-unavailable) |
| Everything DB-backed fails | readiness `db: DOWN`; Hikari connection timeouts | [R3](#r3-postgresql-unavailable) |
| Dead letters | `tallyard_metering_rejected_total{reason="SCHEMA_INVALID"}`; DLT size | [R4](#r4-dead-letter-topic-has-records) |
| Rejections spike | `tallyard_metering_rejected_total` by reason | [R5](#r5-rejections-by-reason) |
| Period stuck | A period in `CLOSING` long after `grace_until`; no `period.rated` events | [R6](#r6-a-period-is-stuck) |
| Webhooks failing | `tallyard_webhooks_deliveries_total{outcome="dead"}`; `tallyard_webhooks_pending` | [R7](#r7-webhook-deliveries-failing) |
| Outbox backlog | `tallyard_outbox_backlog` > 0 and growing | [R8](#r8-outbox-backlog-growing) |
| Invoice disputed | – | [R9](#r9-explain-or-verify-an-invoice) |

## R1. Consumer lag is growing

**Confirm.** Grafana "Consumer lag per partition" climbs; `tallyard_metering_batches_total` is flat
or `tallyard_metering_batch_seconds{quantile="0.99"}` is high. Check worker health:
`curl -s worker:9091/actuator/health/readiness`.

**Causes and actions.**

1. Worker down → restart it (`docker compose restart worker`); lag drains automatically; totals are
   unaffected (effectively-once). Scale out: `docker compose up -d --scale worker=3` (≤ partitions).
2. Database slow → see R3; look at `hikaricp_connections_pending` and PostgreSQL
   `log_min_duration_statement` output.
3. Hot partition (one huge customer) → inherent to per-customer ordering; raise `max-poll-records`
   or increase `tallyard.topics.partitions` for new deployments.

**Verify.** Lag returns to zero; `tallyard_metering_events_total{outcome="stored"}` increases; a
simulator truth check passes.

## R2. Kafka unavailable

**Confirm.** `GET :9091/actuator/health/readiness` shows `kafka.status=DOWN`; ingest returns 503 with
`Retry-After: 5`; `tallyard_ingest_publish_seconds` max hits the 5 s timeout.

**Behaviour.** Nothing is lost: clients hold their events and retry with the same ids. Catalog,
usage reads, entitlements, rating and the dashboard continue to work.

**Action.** Restore the broker (`docker compose start kafka`). Producers reconnect automatically;
consumers rejoin; the outbox publisher drains its backlog.

**Verify.** Readiness `UP`; 503 rate drops to zero; `tallyard_outbox_backlog` returns to zero.

**Drill (local).** `docker compose stop kafka` while the simulator runs → observe 503s and retries →
`docker compose start kafka` → the simulator's final truth check must still pass.

## R3. PostgreSQL unavailable

**Confirm.** Readiness `db.status=DOWN`; API DB-backed endpoints return 500/503; worker logs show the
Kafka error handler retrying (`Backoff … exhausted` never appears — retries are unbounded).

**Behaviour.** Consumers stop acknowledging; no data is lost; ingest still accepts into Kafka.

**Action.** Restore the database; connections recover through Hikari; the consumer's next retry
succeeds.

**Verify.** Lag drains; `tallyard_metering_batches_total` increases; no `duplicate` spike beyond the
redelivered batch (expected and harmless).

## R4. Dead-letter topic has records

**Confirm.** `tallyard_metering_rejected_total{reason="SCHEMA_INVALID"}` increased; inspect:
`docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092
--topic usage.events.v1.dlt --from-beginning --property print.headers=true`.

**Action.** Headers `ty-dlt-reason` and `ty-dlt-source` identify the problem and the original
offset. Since only Tallyard's own producer writes `usage.events.v1`, a DLT record indicates either a
foreign producer or a serialization bug; fix the source. Valid events can be re-submitted through the
API with their original ids (idempotent).

## R5. Rejections by reason

| Reason | Meaning | Action |
|---|---|---|
| `UNKNOWN_SUBJECT` | Subject is not a customer | Create the customer; re-submit events with original ids |
| `NO_ACTIVE_SUBSCRIPTION` | No subscription at event time | Check subscription dates; backdate `startsAt` if appropriate |
| `NO_MATCHING_METER` | No meter for the event type | Create the meter; events stay in the log and can be re-submitted |
| `INVALID_VALUE` | Property missing / not a plain decimal | Fix the producer; see `GET /v1/rejections` detail |
| `OUTSIDE_ACCEPTANCE_WINDOW` | Older than the meter window | Policy; widen `acceptanceWindow` only for new meters |
| `NO_PERIOD` / `NO_OPEN_PERIOD` | Lifecycle gap | Should not happen while a subscription is active; check R6 |

## R6. A period is stuck

**Confirm.** `GET /v1/periods` shows `CLOSING` with `graceUntil` in the past, or `OPEN` with `endsAt`
long past. Worker logs may show `Billing scheduler: rate failed`.

**Action.** Read the logged exception (e.g. plan deleted, DB constraint). Force the step with
`POST /v1/periods/{id}/rate` (ADMIN) and inspect the problem+json. Rating is idempotent and
lock-protected; retrying is safe.

**Verify.** Period becomes `RATED`; `period.rated` appears in webhooks/outbox.

## R7. Webhook deliveries failing

**Confirm.** `GET /v1/webhooks/deliveries` shows `PENDING` with growing `attempts` or `DEAD` with
`lastStatus`/`lastError`. Breaker state is implied by many deliveries re-queued without attempts.

**Action.** Fix the receiver (or the URL via re-registration). Re-queue dead deliveries:
`POST /v1/webhooks/deliveries/{id}/retry`. Payloads are persisted, so nothing needs regenerating.

**Verify.** State `DELIVERED`; `tallyard_webhooks_pending` returns to zero.

## R8. Outbox backlog growing

**Confirm.** `tallyard_outbox_backlog` > 0 for more than a minute while
`tallyard_outbox_failed_total` increases.

**Cause.** Kafka unreachable from the worker, or no worker running.

**Action.** See R2 / R1. Rows are never dropped; `attempts` and `next_attempt_at` in `outbox_event`
show the backoff.

## R9. Explain or verify an invoice

1. `GET /v1/periods/{id}/invoice` — every line carries `breakdownJson` (tiers, units, prices).
2. `GET /v1/periods/{id}/adjustments` — late events that were priced as an adjustment line.
3. `POST /v1/periods/{id}/replay` — recompute from the immutable event log as of rating time;
   `matches=true` with identical hash proves the stored invoice is exactly reproducible. A diff lists
   the lines that differ.

## Routine operations

- **Start/stop:** `make up` / `make down`; **wipe:** `make reset`.
- **Scale workers:** `docker compose up -d --scale worker=N` (N ≤ partitions, default 6).
- **Rotate an API key:** issue a new key (`POST /v1/api-keys`), switch clients, revoke the old one.
- **Backups:** PostgreSQL is the only state that matters; Kafka can be rebuilt from clients re-sending
  (idempotent) and the DB from backups. Standard `pg_dump`/PITR applies.
- **Retention:** `usage_event` grows without bound by default (it is the audit log). Partition by
  month and drop partitions older than the longest acceptance window + legal retention when needed.
