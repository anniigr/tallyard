# Interview Questions and Answers

*Phase 14 deliverable. 48 questions a Senior/Staff interviewer could ask about this repository, with
answers grounded in the implementation. File references are relative to
`tallyard-app/src/main/java/io/tallyard/`.*

## Java and OOP

**1. Where do you use records, and where did you deliberately not?**
Records for immutable views and messages crossing module boundaries (`catalog/*View`,
`ingestion/UsageEventMessage`, `shared/money/Money`) and for command objects
(`catalog/CatalogCommands.New*`). Not for JPA entities (`billing/internal/BillingPeriod`): entities
need identity, mutability through intention-revealing methods (`startClosing()`, `markRated()`) and
a no-args constructor for Hibernate.

**2. How does `Money` avoid floating-point errors and what is its rounding contract?**
`Money` wraps `BigDecimal` normalised to scale 6 (`shared/money/Money`); arithmetic is exact;
`rounded()` applies HALF_EVEN to the currency's minor units and is called once per invoice line.
Mixing currencies throws. Tested in `SharedUnitTests.MoneyTest` and the rating tests.

**3. Why a hand-written UUIDv7 (`shared/id/Ids`)?**
Time-ordered ids keep B-tree inserts on `usage_event` and `outbox_event` append-friendly, unlike
random UUIDv4 which scatters writes across the index. Java 21 has no UUIDv7; the implementation is
20 lines and tested for version bits, ordering and timestamp extraction.

**4. Give an example of sealed/switch pattern matching in the code.**
`metering/internal/ValueExtractor` uses `switch` with type patterns over the property value
(`Map`, `List`, `String`) in `EventValidator.checkDepth`, and `RatingEngine.price` is an exhaustive
`switch` expression over `PricingModel` so a new model fails compilation until handled.

**5. Where is functional style used, and where was it avoided?**
Streams and `Optional` in query/adapter code (`UsageSourceAdapter`, `CatalogService`); the hot
consumer loop (`MeteringService.count`) is imperative on purpose — clear control flow and no
allocation-heavy stream pipelines per event.

**6. How do you prevent abstraction for its own sake?**
There is one SPI (`billing/UsageSource`), introduced only to break a real package cycle. No
repository-over-repository layers; controllers call services; the dashboard aggregator is explicitly
labelled a demonstration layer.

## Spring

**7. How do the two runtime roles work?**
`shared/role/WorkerRole` and `ApiRole` are meta-annotations over `@ConditionalOnExpression` on
`tallyard.role`. Consumers, schedulers, outbox publisher and webhook dispatcher are `@WorkerRole`;
the same jar runs as `api`, `worker` or `all`. ArchUnit enforces that every `@KafkaListener` class
carries `@WorkerRole`.

**8. How is multi-tenancy enforced?**
`tenancy/internal/ApiKeyAuthenticationFilter` resolves the key to a `TenantPrincipal`;
`shared/tenant/TenantContext` exposes it; every service method receives the tenant id from the
context and every query filters by it. Foreign ids resolve to 404. `SecurityIT.tenantsAreIsolated`.

**9. Why `@PreAuthorize("@scopes.has('ADMIN')")` instead of URL-based rules?**
Scope requirements differ per method on the same path (`GET /v1/webhooks` READ vs `POST` ADMIN);
method security keeps the rule next to the handler and testable. The SpEL bean is
`SecurityConfig.ScopeGuard`.

**10. How do catalog and billing coordinate without a dependency cycle when a subscription starts?**
`CatalogService.subscribe` publishes `SubscriptionActivated` via `ApplicationEventPublisher`;
`BillingService.onSubscriptionActivated` is a synchronous `@EventListener` running inside the same
transaction, so the first period is created atomically with the subscription. The dependency is
`billing → catalog` only.

**11. Why `open-in-view=false` and immutable views across modules?**
No lazy-loading outside transactions; other modules receive records (`PlanView` with nested
components/tiers), so there is no chance of a `LazyInitializationException` or an N+1 across module
boundaries.

**12. How does configuration binding work and why a record?**
`shared/config/TallyardProperties` is a `@ConfigurationProperties` record with nested records;
immutable, validated at startup, injected where needed (durations as `Duration`).

## Hibernate, SQL, transactions

**13. Which isolation level do you rely on, and where does it matter?**
PostgreSQL default READ COMMITTED everywhere. Correctness does not depend on a higher level because
every race is closed with explicit locks or constraints: unique key for dedup, `FOR SHARE`/`FOR
UPDATE` for period settlement, `FOR UPDATE` on credit grants, `@Version` on periods.

**14. Explain the `FOR SHARE` / `FOR UPDATE` protocol between metering and rating.**
`BillingPeriodRepository.lockShareCovering` (`PESSIMISTIC_READ` → `FOR SHARE`) is taken by the
consumer for each period it writes into; `lockForUpdate` (`PESSIMISTIC_WRITE`) by rating. Shared
locks are compatible with each other, so batches run concurrently; the exclusive lock waits for them
and blocks newcomers. Result: a batch either commits before rating (and is rated) or sees RATED and
turns its events into adjustments. ADR-003.

**15. How is `SKIP LOCKED` produced through JPA?**
`@Lock(PESSIMISTIC_WRITE)` plus `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout",
value = "-2"))` — Hibernate renders `FOR UPDATE SKIP LOCKED` on PostgreSQL
(`BillingRepositories.lockDueForClosing`, `WebhookDeliveryRepository.lockDue`). The outbox uses the
same clause in plain SQL.

**16. Why did you hit `MultipleBagFetchException`, and what is the fix?**
Join-fetching two `List` collections (plan components and tiers) is ambiguous for Hibernate. The
collections are `Set`s with `@OrderBy("position")` (`catalog/internal/Plan`, `PlanComponent`), which
allows one query for the whole plan — no N+1 when rating.

**17. Where would N+1 have appeared and how is it prevented?**
Loading a plan with components and tiers (fetch joins), loading invoices with lines
(`findByPeriodIdWithLines`), and resolving meters per event (cached lookups in `CatalogService` with a
15-second TTL — the hot path never does a per-event catalog query beyond the first).

**18. What does the bucket upsert look like and why those expressions?**
`UsageEventStore.upsertBuckets`: `INSERT … ON CONFLICT (period_id, meter_id, customer_id,
bucket_start) DO UPDATE SET agg_sum = … + EXCLUDED.agg_sum, agg_min = LEAST(…), agg_max =
GREATEST(…), agg_last = CASE WHEN EXCLUDED.last_occurred_at >= … THEN EXCLUDED.agg_last …`.
`LEAST/GREATEST` ignore NULLs; the `CASE` keeps LAST semantics by event time, not arrival.
Deltas are pre-aggregated per batch in Java and sorted before the batch update to avoid deadlocks.

**19. Why is `usage_event (tenant_id, event_id)` the dedup key rather than a Kafka-side mechanism?**
The write and the check are atomic; the window is unbounded (as long as the log is retained); no
extra store. Kafka idempotent producers only deduplicate retries of the same producer session, not
client retries.

**20. How does replay recompute from the log, and how do you keep SQL and Java in agreement?**
`UsageEventStore.quantityFromLog` builds a per-aggregation `SELECT` with `ingested_at < asOf`,
`ingested_at - occurred_at <= make_interval(secs => window)` and the regex
`^-?[0-9]{1,19}(\.[0-9]{1,6})?$`; `ValueExtractor.PLAIN_DECIMAL` is the same grammar. Both are
documented side by side; `ValueExtractorTest` pins the Java side, `BillingLifecycleIT` the agreement.

**21. Why are hashes stored as `varchar(64)` not `char(64)`?**
Hibernate schema validation reports `bpchar` for `char(n)` and rejects it against a `String`
mapping; we keep `ddl-auto=validate` strict and use `varchar`.

**22. Which indexes exist and why?**
See `V1__core_schema.sql`: unique `(tenant_id, event_id)`; `(tenant_id, subject, occurred_at)` for
replay scans; partial unique `billing_period_one_open`; `(state, ends_at)` / `(state, grace_until)`
for scheduler scans; partial `outbox_event_pending_idx` on unpublished rows; partial
`webhook_delivery_due_idx`. Each matches one query path; none are speculative.

## Kafka and delivery semantics

**23. What delivery guarantee does Tallyard provide end to end?**
At-least-once transport, idempotent transactional sink → effectively-once accounting. Explicitly not
"exactly-once" (ADR-002). Tested by `ConsumerRedeliveryIT`, which throws between commit and ack.

**24. Why a batch listener with manual acks?**
One transaction per batch amortises commits and makes the ack rule trivial (ack after commit).
`spring.kafka.listener.type=batch`, `ack-mode=manual_immediate`.

**25. What happens to a malformed record?**
Parsed per record in `UsageEventConsumer`; failures are forwarded to `usage.events.v1.dlt` with
`ty-dlt-reason`/`ty-dlt-source` headers and recorded as `SCHEMA_INVALID`. The batch continues. Whole
batches are never dead-lettered — that would turn an outage into data loss.

**26. What is the partition key and what does it buy?**
`tenantId:subject`. Per-customer ordering and single-writer bucket updates for a customer; hot
customers are bounded by one partition — the price of ordering.

**27. How would you evolve the message schema?**
`ty-schema-version` header plus `schemas/usage-event.v1.schema.json`; a v2 would be a new consumer
branch keyed on the header with both versions accepted during migration. `ContractIT` validates
samples against the schema.

**28. How does the outbox publisher achieve ordering and at-least-once?**
Rows are claimed in `created_at` order with `SKIP LOCKED`, published keyed by `aggregate_id`, acked,
then marked published in the same transaction. A crash between ack and commit republishes —
consumers dedupe on `eventId` (`webhook_delivery` unique key).

**29. What does the consumer do when PostgreSQL is down?**
`DefaultErrorHandler` with `ExponentialBackOff` (0.5 s → 30 s cap, unlimited) retries the batch;
nothing is acked; lag grows; recovery is automatic. `KafkaConsumerConfig`.

## Distributed consistency and concurrency

**30. How do you guarantee exactly one invoice per period under concurrent schedulers?**
Three layers: `FOR UPDATE SKIP LOCKED` claim, `@Version` on `billing_period`, and
`invoice_draft.period_id UNIQUE`. `BillingLifecycleIT.concurrentRating…` fires six concurrent
requests: one 200, five 409.

**31. How can a prepaid balance never go negative?**
`CreditGrantRepository.lockUsable` locks grants `FOR UPDATE` in a deterministic order (expiry,
priority, age); `CreditGrant.consumeUpTo` never takes more than remaining; the DB
`CHECK (amount_consumed <= amount_granted)` is the last line. `CreditLedgerConcurrencyIT` runs 16
threads against $100.

**32. Where is eventual consistency and how is it bounded?**
Ingest → counted: consumer latency (tens to hundreds of ms). Counted → entitlement: cache TTL (2 s).
Rated → webhook: seconds. Each is measured (`tallyard.metering.batch`, `tallyard.entitlements.*`,
`tallyard.webhooks.*`).

**33. How do late events interact with replay?**
Replay reads the log as of `rated_at`; a late event has `ingested_at > rated_at`, so it is excluded
from the comparison and instead appears as an adjustment on the open period. Buckets of settled
periods are never modified. `BillingLifecycleIT.lateEvent…`.

**34. Why not re-open periods?**
Immutability of issued invoices; downstream systems already received `invoice.drafted`. Industry
practice (adjustment lines / credit notes). ADR-003.

**35. Is anything idempotent on the HTTP side?**
Ingest is idempotent by event id. Period commands are state-transition commands: repeating `rate` on
a rated period is a 409, not a second invoice. Webhook retry re-queues; a second retry of a delivered
row re-sends (operator action, intentional).

## Resilience and operations

**36. What happens when Kafka is unavailable?**
Ingest fails fast with 503 + `Retry-After: 5` (`IngestService.publish` timeout); readiness drops
`kafka` (`KafkaHealthIndicator`); DB-backed features keep working; outbox backs off. Clients retry
with the same ids.

**37. How do you protect the system from a flapping webhook endpoint?**
Resilience4j circuit breaker per endpoint (`WebhookDispatcher`): 50 % failure rate over 10 calls
opens it for 30 s; due deliveries are pushed back without counting attempts. Independent of the
per-delivery exponential backoff and dead-letter threshold.

**38. How is rate limiting implemented and what are its limits?**
`ingestion/internal/TokenBucket` — GCRA-style, lock-free, per API key, one-second burst; 429 with
`Retry-After`. Per node by design; a global limit would need a shared store.

**39. Which health checks exist and what does readiness include?**
Liveness and readiness probes; readiness = `readinessState` + `db` + custom `kafka`
(`describeCluster` with 2 s timeout). Compose uses readiness for `depends_on`.

**40. How would you debug "customer X says usage is wrong"?**
Runbook R9: `GET /v1/periods/{id}/usage`, `/adjustments`, `/invoice` breakdowns, then
`POST /replay`. Then rejections for that subject (`GET /v1/rejections`) — most "missing" usage is a
rejection with a reason.

## Security

**41. How are API keys stored and compared?**
SHA-256 hex in `api_key.key_hash` (unique); lookup by hash; 30 s cache. Secret shown once. Bootstrap
token compared with `MessageDigest.isEqual`.

**42. How are webhook secrets protected and verified?**
AES-256-GCM with per-row IV (`SecretEncryptor`), key from environment; signature
`t=…,v1=HMAC-SHA256(secret, t.body)`; constant-time comparison in `WebhookSigner.verify`.

**43. What is deliberately public and why is that acceptable?**
Static UI, OpenAPI docs, actuator on the management port, the demo webhook sink (POST only). Each is
either documentation or lives on a port deployments keep internal; documented in
`docs/security/security-review.md` with the follow-ups (SSRF list, scanning, key rotation).

## Testing and performance

**44. Why Testcontainers instead of H2 or embedded Kafka?**
The behaviours under test are PostgreSQL-specific (`ON CONFLICT … RETURNING`, `FOR SHARE`,
`SKIP LOCKED`, partial indexes) and Kafka-specific (redelivery, rebalances). Containers are singletons
per JVM; isolation is by tenant, not truncation (`support/TestTenant`).

**45. How do you test the crash-between-commit-and-ack window?**
`metering/internal/MeteringHooks.afterCommit` is a seam called after the transaction and before the
ack; `ConsumerRedeliveryIT` makes it throw twice and asserts totals and `storedEvents` afterwards.

**46. What do the property tests prove that examples do not?**
Over random tier tables: graduated is non-negative and monotonic in quantity; equals an independent
band-by-band computation; volume ≤ graduated for decreasing prices; included units never increase a
bill; package = ceil(billable/size) × price; determinism of the hash.

**47. Where is the performance bottleneck and how did you measure it?**
Ingest publish latency (Kafka acks) and the metering batch transaction (unique-index insert + bucket
upsert). Measured with k6 (`loadtest/`) and Micrometer timers; results and the environment are in
`docs/performance`. Never extrapolated.

**48. What would you change after six months in production?**
Partition `usage_event` monthly and `meter_bucket` by period hash; move entitlement snapshots to a
shared cache invalidated by the consumer if API nodes multiply; add the deterministic anomaly
detector as an outbox event source; wire image/dependency scanning; add SSRF protection for webhook
URLs. What I would **not** do: split into microservices or add a workflow engine — neither solves a
problem the system has.
