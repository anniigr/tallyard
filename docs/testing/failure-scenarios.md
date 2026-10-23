# Failure Scenarios

*Each scenario: how it is provoked, the expected behaviour, and where it is
verified — automatically (test class) or as a manual drill with the recorded procedure.*

| # | Scenario | Provoked by | Expected behaviour | Verification |
|---|---|---|---|---|
| 1 | Duplicate events (same id, same batch) | Batch containing the same event twice | One row, one increment | `MeteringPipelineIT.duplicateEventIdsAreCountedOnce` |
| 2 | Duplicate events (client retries the whole batch) | Same batch posted 3× | Totals unchanged after first | same |
| 3 | Concurrent duplicate submission | 8 threads post the same 200-event batch | 200 counted | `MeteringPipelineIT.concurrentSubmission…` |
| 4 | Consumer crash after commit, before ack | `MeteringHooks.afterCommit` throws twice | Redelivered batches are no-ops; exactly 120 counted; `storedEvents == 120` | `ConsumerRedeliveryIT` |
| 5 | Out-of-order events | Reverse-ordered batch; later storage event first | Same totals as sorted; MAX unaffected | `MeteringPipelineIT.outOfOrder…` |
| 6 | Late event for a RATED period | Event with `occurredAt` inside a rated window | Adjustment on the open period, original period untouched, replay still matches, ADJUSTMENT line at next rating | `BillingLifecycleIT.lateEvent…` |
| 7 | Event older than acceptance window / before subscription | 40-day-old event | Rejected with reason, kept in log | `MeteringPipelineIT.semanticallyInvalid…` |
| 8 | Unknown subject / meter / non-numeric value | Crafted events | Rejected with `UNKNOWN_SUBJECT`, `NO_MATCHING_METER`, `INVALID_VALUE` | same |
| 9 | Structurally invalid items in a batch | Future timestamp, bad patterns | Per-item `INVALID` (202) or 400 with field errors; batch not failed | `MeteringPipelineIT.structurallyInvalid…`, `IngestApiIT.malformedItems…` |
| 10 | Poison Kafka record (not JSON / missing fields) | Raw producer writes garbage to the topic | Dead-lettered with headers, `SCHEMA_INVALID` rejection, next valid event still processed | `IngestApiIT.unparseableKafkaRecords…` |
| 11 | Two schedulers rate the same period | 6 concurrent `POST …/rate` | One 200, five 409, one invoice | `BillingLifecycleIT.concurrentRating…` |
| 12 | Illegal state transitions | finalize OPEN, rate RATED | 409 `period.illegal_transition` | `BillingLifecycleIT.illegalTransitions…` |
| 13 | Concurrent credit consumption | 16 threads apply $9 against $100 | Exactly $100 consumed; balance 0; DB check never violated | `CreditLedgerConcurrencyIT` |
| 14 | Webhook endpoint returns 500 | WireMock stub | 3 attempts with backoff, then DEAD; manual retry delivers | `WebhookDeliveryIT.failingEndpoint…` |
| 15 | Webhook endpoint flaps (503 then 200) | WireMock scenario | Delivered on attempt 2, no duplicate | `WebhookDeliveryIT.transientFailure…` |
| 16 | Transaction rolled back after outbox append | Exception after `append` | Nothing published | `OutboxPublicationIT.rolledBack…` |
| 17 | Outbox used outside a transaction | Direct call | `IllegalTransactionStateException` | `OutboxPublicationIT.appendOutside…` |
| 18 | Rate limit exceeded | 4 × 1000 events within a second against a 2500/s key | 429 with `Retry-After` | `IngestApiIT.perKeyRateLimit…` |
| 19 | Wrong / missing / revoked key, wrong scope, foreign tenant ids | Various | 401 / 403 / 404 as designed | `SecurityIT` |
| 20 | Entitlement limit exceeded; credit exhausted | Usage beyond `hardLimit`; prepaid charges > balance | DENY with reason within TTL | `EntitlementIT` |
| 21 | Kafka unavailable during ingest | `docker compose stop kafka` while the simulator runs | 503 + `Retry-After`; readiness `kafka: DOWN`; simulator retries; after `start kafka` the truth check passes | Manual drill (procedure below) |
| 22 | Worker crashes under load | `kill -9` of the worker JVM inside its container during the simulator run; Compose `restart: unless-stopped` restarts it | Lag grows then drains; truth check passes | Manual drill (procedure below) |
| 23 | PostgreSQL unavailable | `docker compose stop postgres` | API 5xx; consumer retries with backoff; no acks; recovers on `start` | Manual drill |
| 24 | Two workers, rebalance | `--scale worker=2` mid-run | Per-partition ordering preserved; no double counting | Manual drill |

## Manual drill procedure (recorded in `docs/engineering-progress.md` when executed)

```bash
make up                                   # full stack
java -jar tallyard-simulator/target/tallyard-simulator.jar --key $KEY --rate 500 --duration 90 &
sleep 20; docker exec tallyard-worker-1 sh -c 'kill -9 $(pidof java)'   # crash the JVM; Compose restarts the container
# (docker kill / docker stop count as operator stops and are NOT auto-restarted by Docker; the stack
#  runs the JVM under an init process so a JVM crash terminates the container)
# optionally: docker compose -f deploy/docker-compose.yml stop kafka; sleep 10; ... start kafka
wait                                      # simulator prints the truth check
```

Expected final line: `ALL 20 CUSTOMERS MATCH: Tallyard's live totals equal the simulator's expected totals exactly.`
