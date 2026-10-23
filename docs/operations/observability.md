# Observability

## Signals

| Kind | Implementation | Where |
|---|---|---|
| Metrics | Micrometer → Prometheus registry, scraped from `:9091/actuator/prometheus` on every node (`role` tag distinguishes api/worker) | Prometheus `:9090`, Grafana `:3000` |
| Traces | Micrometer Tracing → OpenTelemetry → OTLP/HTTP → Jaeger 2. Spring Kafka observation is enabled on both producer and listener, so a trace spans HTTP ingest → Kafka produce → consume → JDBC | Jaeger `:16686` |
| Logs | Logback via Spring Boot; `TALLYARD_LOG_FORMAT=ecs|logstash` switches to structured JSON. MDC carries `tenant` (from the API key) and trace/span ids | stdout → `docker compose logs` |
| Health | Liveness `/actuator/health/liveness`; readiness `/actuator/health/readiness` = `readinessState` + `db` + `kafka` (custom `KafkaHealthIndicator` using `describeCluster` with a 2 s timeout) | Compose healthchecks, orchestrator probes |

## Custom metrics

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `tallyard.ingest.events` | counter | `result=accepted|invalid` | Events accepted into Kafka vs. reported invalid |
| `tallyard.ingest.rate_limited` | counter | | Requests refused with 429 |
| `tallyard.ingest.publish` | timer (p50/p95/p99) | | Time to receive broker acks for a batch |
| `tallyard.metering.events` | counter | `outcome=stored|duplicate|late` | The three fates of a consumed event |
| `tallyard.metering.rejected` | counter | `reason=…` | Rejections by `RejectionReason` |
| `tallyard.metering.batch` | timer (p50/p95/p99) | | Transactional processing time per batch |
| `tallyard.metering.batches` | counter | | Batches processed |
| `tallyard.billing.rating` / `tallyard.billing.replay` | timer | | Rating / replay duration |
| `tallyard.billing.failures` | counter | | Lifecycle steps (close/rate/finalize) that threw and will be retried next tick |
| `tallyard.billing.overdue_periods` | gauge | | Periods still due after the last scheduler tick; non-zero for more than a few ticks means a stuck period (runbook R6) |
| `tallyard.entitlements.check` | timer (p50/p95/p99) | | Hot-path latency |
| `tallyard.entitlements.decisions` | counter | `decision=ALLOW|DENY` | |
| `tallyard.entitlements.snapshot_misses` | counter | | Cache misses (snapshot recomputed) |
| `tallyard.outbox.published` / `tallyard.outbox.failed` | counter | | Outbox publication outcomes |
| `tallyard.outbox.backlog` | gauge | | Unpublished rows at last poll |
| `tallyard.webhooks.deliveries` | counter | `outcome=delivered|failed|dead` | |
| `tallyard.webhooks.pending` | gauge | | Deliveries waiting |

Plus the standard sets: `http_server_requests_seconds` (per URI/status), `kafka_consumer_*`
(including `records_lag_max` per partition), `kafka_producer_*`, `hikaricp_*`, `jvm_*`,
`process_*`.

## Grafana dashboard

`deploy/grafana/dashboards/tallyard-operations.json` is provisioned automatically. Panels:

1. Stat row — ingest events/s, stored/s, duplicates/s, consumer lag, outbox backlog, webhooks pending
2. Ingest accepted vs invalid
3. Metering outcomes (stored / duplicate / late)
4. Rejected events by reason
5. Consumer lag (Kafka `records-lag-max`, max per consumer thread — a windowed maximum, so it reports the backlog seen during the last ~60 s)
6. Latency p99: ingest publish, metering batch, entitlement check, rating
7. HTTP p99 by endpoint
8. Entitlement decisions
9. Webhook deliveries by outcome
10. Outbox published/failed
11. Hikari connections and JVM heap per role

Every panel is driven by metrics the code emits; none are placeholders. Screenshots taken during the
recorded demo are in `docs/assets/` (see README).

## Tracing

Sampling is `TALLYARD_TRACE_SAMPLING` (Compose default 0.2; set 1.0 to trace everything during a
demo). A typical ingest trace contains: `http post /v1/events` → `usage.events.v1 send` (one span per
record) → `usage.events.v1 process` (consumer, linked via Kafka headers) → JDBC spans for the log
insert and bucket upsert. The tenant id is a span attribute via MDC propagation.

## Alerting suggestions (not configured)

| Alert | Expression | Severity |
|---|---|---|
| Counting stalled | `max(kafka_consumer_fetch_manager_records_lag_max) > 10000 for 5m` | page |
| Ingest failing | `sum(rate(http_server_requests_seconds_count{uri="/v1/events",status=~"5.."}[5m])) > 1` | page |
| Dead letters | `increase(tallyard_metering_rejected_total{reason="SCHEMA_INVALID"}[10m]) > 0` | ticket |
| Outbox stuck | `tallyard_outbox_backlog > 0 for 5m` | page |
| Period stuck | `tallyard_billing_overdue_periods > 0 for 10m` or `increase(tallyard_billing_failures_total[10m]) > 0` | page |
| Webhooks dead | `increase(tallyard_webhooks_deliveries_total{outcome="dead"}[1h]) > 0` | ticket |
| Entitlement latency | `tallyard_entitlements_check_seconds{quantile="0.99"} > 0.05 for 5m` | ticket |
