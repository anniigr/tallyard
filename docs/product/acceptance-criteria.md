# Tallyard — Acceptance Criteria

*Phase 4 deliverable, 2026-10-23. Each criterion names the test or procedure that verifies it. The
"Verified by" column is filled in as implementation progresses; see `docs/engineering-progress.md`.*

## A. Counting correctness

| ID | Criterion | Verified by |
|---|---|---|
| A1 | Submitting the same event twice (same `eventId`) results in exactly one `usage_event` row and one increment of the bucket | `MeteringDuplicateIT` |
| A2 | Submitting the same batch N times concurrently yields identical totals to submitting once | `MeteringConcurrentDuplicateIT` |
| A3 | A consumer that throws after the DB commit and before the ack does not double count on redelivery | `ConsumerRedeliveryIT` |
| A4 | Events out of order within a batch produce the same buckets as the sorted batch | `MeteringOrderingIT` |
| A5 | An event for a RATED period becomes a `usage_adjustment` on the current OPEN period | `LateEventPolicyIT` |
| A6 | An event older than the acceptance window is rejected with `OUTSIDE_ACCEPTANCE_WINDOW` and published to the DLT | `LateEventPolicyIT`, `DeadLetterIT` |
| A7 | An invalid payload does not fail the batch; it is rejected and dead-lettered | `DeadLetterIT` |
| A8 | Aggregations SUM, COUNT, MIN, MAX, LAST, UNIQUE_COUNT produce the mathematically expected values | `AggregationTest`, `MeteringAggregationIT` |

## B. Periods and rating

| ID | Criterion | Verified by |
|---|---|---|
| B1 | A new subscription has exactly one OPEN period covering `startsAt` | `SubscriptionServiceIT` |
| B2 | Illegal transitions (e.g. RATED → OPEN) are rejected | `BillingPeriodTest` |
| B3 | Two concurrent rating attempts on the same period produce exactly one invoice draft | `ConcurrentRatingIT` |
| B4 | GRADUATED, VOLUME, PER_UNIT, PACKAGE, FLAT and minimum commitment produce the documented amounts for worked examples | `RatingEngineTest` |
| B5 | For any plan and quantity, graduated total is monotonic non-decreasing in quantity and never negative; volume ≤ graduated when tier prices are non-increasing | `RatingEnginePropertyTest` (jqwik) |
| B6 | Rating the same inputs twice yields identical lines and hash | `RatingEngineTest`, `ReplayIT` |
| B7 | Replay of a rated period reports `matches: true` when the event log is unchanged, and `matches: false` with a diff when a bucket is tampered with | `ReplayIT` |
| B8 | Rated period emits `period.rated` and `invoice.drafted` through the outbox exactly once per period | `OutboxPublicationIT` |

## C. Credits and entitlements

| ID | Criterion | Verified by |
|---|---|---|
| C1 | Concurrent consumption of a credit grant never drives `amountConsumed` above `amountGranted` | `CreditLedgerConcurrencyIT` |
| C2 | Credits are applied in `expiresAt`, then `priority` order | `CreditApplicationTest` |
| C3 | Entitlement check denies once `usage + requested > hardLimit` | `EntitlementIT` |
| C4 | Entitlement answers reflect new usage within the configured staleness bound | `EntitlementStalenessIT` |
| C5 | Entitlement p99 < 20 ms at 500 req/s on the reference laptop | k6 `entitlement.js` (documented in `docs/performance`) |

## D. Ingestion API

| ID | Criterion | Verified by |
|---|---|---|
| D1 | Batch of 1000 valid events returns 202 with 1000 `accepted` statuses | `IngestApiIT` |
| D2 | Mixed batch returns per-event `accepted`/`invalid` with field-level errors | `IngestApiIT` |
| D3 | Missing/invalid API key → 401; key without `INGEST` scope → 403 | `SecurityIT` |
| D4 | Kafka unavailable → 503 with `Retry-After` | `KafkaOutageIT` (Testcontainers stop/start) |
| D5 | Exceeding the key's rate limit → 429 | `RateLimitIT` |
| D6 | Generated OpenAPI matches the committed `openapi.yaml` | `OpenApiContractTest` |
| D7 | Event JSON schema (`usage.events.v1`) validates samples and rejects invalid ones; schema version is in headers | `EventContractTest` |

## E. Webhooks and outbox

| ID | Criterion | Verified by |
|---|---|---|
| E1 | Outbox events are published in insertion order per aggregate and marked sent | `OutboxPublicationIT` |
| E2 | Outbox publisher survives a Kafka send failure and retries without duplication side effects | `OutboxRetryIT` |
| E3 | Webhook delivery retries with backoff on 5xx and dead-letters after N attempts | `WebhookDeliveryIT` (WireMock) |
| E4 | Webhook payload carries a valid HMAC-SHA256 signature | `WebhookSignatureTest` |

## F. Operability

| ID | Criterion | Verified by |
|---|---|---|
| F1 | `docker compose up` brings the stack to healthy within 2 minutes on the reference laptop | Manual, recorded in `docs/operations` |
| F2 | Liveness stays UP and readiness goes DOWN while Kafka is stopped | `KafkaOutageIT` + manual |
| F3 | Prometheus exposes the custom metrics listed in the specification | `MetricsIT` |
| F4 | A trace spans HTTP ingest → Kafka produce → consume → DB | Manual with Jaeger, screenshot |
| F5 | Grafana dashboard panels show non-empty data during the demo | Manual, screenshot |

## G. Engineering quality

| ID | Criterion | Verified by |
|---|---|---|
| G1 | Spring Modulith module structure verifies (no cyclic or illegal dependencies) | `ModularityTest` |
| G2 | ArchUnit: domain packages do not depend on Spring web/JPA; controllers do not access repositories directly | `ArchitectureTest` |
| G3 | All tests pass in CI on a clean runner | GitHub Actions |
| G4 | No secrets in the repository | gitleaks step in CI |
| G5 | Formatting enforced | Spotless check in CI |
