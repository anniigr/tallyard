# Performance

*Every number in this document was measured on the environment below on
2026-10-23 with the commands shown; raw outputs are in `raw/`. Nothing is extrapolated. These are
laptop numbers inside Docker Desktop and should be read as "the system behaves as designed under
load", not as production capacity figures.*

## Environment

| | |
|---|---|
| Machine | Apple M3 (8 cores), 16 GiB RAM, macOS 27.0.1 |
| Docker | Docker Desktop 29.7.2, VM limited to 8 CPUs / 7.7 GiB |
| Stack | `deploy/docker-compose.yml`: 1 × api, 1 × worker (3 consumer threads), PostgreSQL 17 (`shared_buffers=256MB`, `synchronous_commit=on`), Kafka 4.1 KRaft (6 partitions, `acks=all`), Prometheus, Grafana, Jaeger — all on the same VM as the load generators |
| Application | Tallyard 0.1.0, Java 21 (Temurin JRE in container, ZGC, `MaxRAMPercentage=70`), trace sampling 0.2 |
| Generators | `tallyard-simulator` (JDK HttpClient, 4 threads) on the host JVM; k6 in the `grafana/k6` container with `--network host` |

The load generators, the brokers, the database and the application share the same 8 cores, so
latencies include contention that a real deployment would not have.

## Scenario 1 — simulator chaos drill (correctness under load and failure)

```bash
java -jar tallyard-simulator/target/tallyard-simulator.jar --url http://localhost:8080 --key $KEY \
     --customers 20 --rate 500 --duration 90 --duplicates 0.05 --late 0.02 --invalid 0.01 --seed 42
# t+25 s: docker exec tallyard-worker-1 sh -c 'kill -9 $(pidof java)'   (10:50:28 UTC)
#         Compose restart policy brought the container back within 3 s; healthy again at 10:50:51
```

Raw: `raw/simulator-chaos-run.txt`, `raw/chaos-timeline.txt`.

| Measure | Value |
|---|---|
| Events sent (including injected duplicates) | 49,104 |
| Accepted by the API (202) | 49,104 — 0 × 503, 0 × 429, 0 retries |
| Injected duplicates / late / invalid-value events | 4,104 / 937 / 459 |
| Ingest batch latency (100 events, host → api → Kafka acks) p50 / p95 / p99 | 19.6 ms / 37.8 ms / 64.2 ms |
| Worker outage | JVM killed with SIGKILL mid-batch; container restarted by the Compose policy in ~3 s, readiness healthy ~23 s after the kill |
| Peak consumer lag during the outage (Kafka `records_lag_max`) | 3,073 records on the busiest partition (`raw/prometheus-consumer-lag-max.json` holds an earlier run with a 21 s manual restart: 4,142) |
| Time for lag to drain after restart | < 10 s (truth check succeeded on its first poll after drain) |
| Consumer batch transaction p50 / p99 (`tallyard.metering.batch`) | 88 ms / 226 ms (maximum of the per-minute percentiles over the run; the p50 peak is the catch-up burst of full 500-record batches after restart) |
| Rejections recorded (from `rejected_event`) | 937 × `NO_ACTIVE_SUBSCRIPTION` (the simulator's "late" events predate the demo subscriptions, which were created seconds before the run — no period had been rated yet, so they are rejections rather than adjustments; the adjustment path is covered by `BillingLifecycleIT`), 459 × `INVALID_VALUE` (the injected non-numeric `tokens`) |
| **Truth check** | **ALL 20 CUSTOMERS MATCH** — live totals equal the simulator's expected totals exactly |

The outage window shows up in Grafana as a lag spike that returns to zero; `duplicate` outcomes
spike briefly at restart (the redelivered in-flight batch), `stored` does not double.

## Scenario 2 — k6 ingest load (`loadtest/ingest.js`)

```bash
docker run --rm -i --network host -e API_KEY=$KEY -e RATE=20 -e BATCH=100 -e DURATION=60s \
  -v $(pwd)/loadtest:/scripts grafana/k6 run /scripts/ingest.js        # raw/k6-ingest.txt
```

Constant arrival rate: 20 batches/s × 100 events ≈ **2,000 events/s** for 60 s (≈5 % duplicates
inside batches), 20 pre-allocated VUs.

| Measure | Value | Threshold |
|---|---|---|
| Accepted events | 126,152 (2,102/s) | – |
| Requests / failures | 1,200 / 0 (0.00 %) | < 1 % |
| HTTP latency p50 / p95 / p99 / max | 9.4 ms / 12.2 ms / **16.8 ms** / 38.0 ms | p95 < 150 ms, p99 < 300 ms |
| VUs needed to sustain the rate | ≤ 1 of 20 | – |
| Consumer: everything counted, lag back to 0 within seconds after the run | yes (`/v1/usage/stats` and `records_lag_max`) | – |
| Peak stored rate on the worker (`rate(stored[30s])`) | 2,000 events/s with a single worker (i.e. the full offered load) | – |

Each 100-event request costs ~10 ms end to end including 100 Kafka produces with `acks=all` (the
producer batches them: `linger.ms=5`, `batch.size=64 KiB`, lz4). The ingestion tier is far from
saturated at this rate; the generator (k6) and the broker share the CPU with it.

## Scenario 3 — k6 entitlement checks (`loadtest/entitlement.js`)

```bash
docker run --rm -i --network host -e API_KEY=$KEY -e RATE=500 -e DURATION=60s \
  -v $(pwd)/loadtest:/scripts grafana/k6 run /scripts/entitlement.js  # raw/k6-entitlement.txt
```

Constant **500 requests/s** for 60 s over 20 customers × 2 meters (40 distinct snapshots, 2 s TTL).

| Measure | Value | Threshold |
|---|---|---|
| Requests / failures | 29,995 / 0 (0.00 %) | < 0.1 % |
| Latency p50 / p95 / p99 / max | **0.27 ms** / 1.67 ms / **6.05 ms** / 138.7 ms | p50 < 10 ms, p99 < 20 ms |
| Dropped iterations | 6 (0.1/s, k6 scheduling) | – |

The p99 is dominated by snapshot recomputation on cache expiry (every 2 s per key: usage query +
live rating for prepaid customers); the max is a cold-start outlier. Hits are sub-millisecond.

## Bottleneck analysis

Observed during the runs (Grafana "Tallyard — Operations", Prometheus queries in
`docs/operations/observability.md`):

1. **Ingest path**: cost is the Kafka round trip with `acks=all`; CPU on the api node stayed low.
   Latency scaled with batch size, not request rate, as expected for a producer-bound path.
2. **Metering path**: one worker kept up with the full 2,000 stored events/s while k6 shared the
   machine; batch p50 41 ms for up to 500 records = one multi-row `INSERT … RETURNING` + a
   handful of `FOR SHARE` lookups + batched upserts. The p99 (192 ms) occurred during catch-up after
   the worker restart, when batches were full and PostgreSQL was also serving the API.
3. **PostgreSQL**: `synchronous_commit=on`; one commit per batch keeps fsyncs low. Hikari pool never
   exceeded a handful of active connections on the worker.
4. **Entitlements**: pure cache arithmetic on hits; misses are bounded by one usage query and one
   in-memory rating.

## Tuning applied during development

| Change | Effect |
|---|---|
| Multi-row `INSERT … ON CONFLICT DO NOTHING RETURNING` (250 rows per statement) instead of per-row inserts | One round trip per chunk; dedup result in the same statement |
| Pre-aggregation of bucket deltas per (period, meter, customer, hour) before the batched upsert | Upserts per batch ≈ distinct customers × meters, not events |
| Short-TTL Caffeine caches for catalog lookups on the consumer path | No per-event catalog queries |
| Producer `linger.ms=5`, `batch.size=64 KiB`, lz4, idempotent | Hundreds of records per broker request |
| Kafka listener `max-poll-records=500`, concurrency 3 | Larger transactions, amortised commits |
| UUIDv7 primary keys | Append-friendly B-tree inserts on the hot tables |

## What was not measured

- The throughput ceiling: at higher rates the host-side generator and the shared VM become the
  bottleneck before the application does.
- Multi-worker scaling (one worker in all runs; partitions = 6).
- Sustained multi-hour runs (index bloat, `usage_event` growth, vacuum behaviour).
- Replay cost on very large periods (ADMIN audit operation, not a hot path).
