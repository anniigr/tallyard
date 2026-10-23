# Architectural Patterns for a Spring Boot + PostgreSQL + Kafka Backend

Verification date: 2026-10-23. Reference implementations cite files inspected in the repositories surveyed in `open-source-landscape.md`; paths are relative to each repository root at `HEAD` on that date.

Each pattern is described with: the problem, the guarantees it does and does not provide, where it earns its complexity, where it does not, a concrete reference implementation, and the typical Spring Boot + PostgreSQL + Kafka shape.

## 0. Why "exactly-once end-to-end" is usually a false claim

Kafka offers *exactly-once semantics* (EOS) only for the closed loop "consume from Kafka, process, produce to Kafka" using idempotent producers and transactions (`read_process_write` with `isolation.level=read_committed`). The moment a side effect leaves that loop — a PostgreSQL write, an HTTP call, an email — the Kafka transaction cannot cover it. `confluentinc/parallel-consumer` tests this boundary explicitly (`TransactionAndCommitModeTest`, `RebalanceEoSDeadlockTest`), and Flink's two-phase-commit sinks require sinks that participate in the protocol.

What can honestly be guaranteed in the target stack:

| Boundary | Honest guarantee | Mechanism |
|----------|------------------|-----------|
| Business write + event record | Atomic | Outbox row written in the same PostgreSQL transaction |
| Outbox → Kafka | At-least-once, ordered per key | Relay (polling with `FOR UPDATE SKIP LOCKED`, or Debezium CDC) + idempotent producer |
| Kafka → consumer side effect | Effectively-once | Idempotent consumer: dedupe table keyed by message id, written in the same transaction as the side effect |
| External HTTP call | At-least-once with idempotency key | Caller-generated key, provider-side dedupe; otherwise at-most-once |

The correct wording is "at-least-once delivery with idempotent processing, yielding effectively-once outcomes for state we own". The Allegro post "Transactions Aren't Enough" (2025-12) argues the same end-to-end view.

## 1. Transactional outbox

- **Problem:** dual write — saving an entity and publishing an event are two systems; a crash between them loses or fabricates events.
- **Guarantees:** the event is persisted iff the business change commits; events are eventually published; per-aggregate order is preserved if the relay publishes in insertion order with the aggregate id as Kafka key.
- **Does not guarantee:** no duplicates (relay may re-send after a crash), global ordering across aggregates, delivery latency bounds (polling interval or CDC lag).
- **Adds value:** whenever an event drives another service's state (payments, notifications, inventory, search index).
- **Unnecessary complexity:** events that are purely telemetry, or a single-module monolith where in-process `@TransactionalEventListener(AFTER_COMMIT)` suffices and loss is acceptable.
- **Reference implementations:**
  - `spring-projects/spring-modulith`: `spring-modulith-events/spring-modulith-events-jdbc/src/main/resources/org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql` (table `event_publication` with `status`, `completion_attempts`, `last_resubmission_date`), `JdbcEventPublicationRepository.java`, `spring-modulith-events-kafka/.../KafkaEventExternalizerConfiguration.java`.
  - `gruelbox/transaction-outbox`: `transactionoutbox-core/src/main/java/com/gruelbox/transactionoutbox/DefaultDialect.java` (`selectBatch`, `lock ... FOR UPDATE` with version, `fetchNextSequence` for ordered topics), `DefaultPersistor.java` (`attempts`, `blocked`, `processed`, `nextAttemptTime`).
  - `eventuate-tram/eventuate-tram-core`: `eventuate-tram-in-memory/src/main/resources/eventuate-tram-embedded-schema.sql` (`message` table) with an external CDC relay.
  - `debezium/debezium-examples`: `outbox/register-postgres.json` + `debezium-connect-plugins/src/main/java/io/debezium/transforms/outbox/EventRouter.java` in `debezium/debezium`.
  - Minimal variant: `ddd-by-examples/library` `src/main/java/io/pillopl/library/commons/events/publisher/StoreAndForwardDomainEventPublisher.java` (scheduled every 3 s).
  - Course variant: `sivaprasadreddy/spring-boot-microservices-course` `order-service/src/main/java/com/sivalabs/bookstore/orders/jobs/OrderEventsPublishingJob.java` (ShedLock-guarded poller).
- **Spring Boot + PostgreSQL + Kafka shape:** `outbox(id uuid pk, aggregate_type, aggregate_id, event_type, payload jsonb, headers jsonb, created_at, published_at null, attempts int)`; insert inside the `@Transactional` service method; a poller selects `WHERE published_at IS NULL ORDER BY created_at LIMIT n FOR UPDATE SKIP LOCKED`, sends with `KafkaTemplate` (acks=all, `enable.idempotence=true`, key = aggregate_id), then marks rows in the same relay transaction; or hand the table to Debezium with the EventRouter SMT. Expose `outbox_backlog` and `outbox_oldest_age_seconds` as Micrometer gauges.

## 2. CQRS

- **Problem:** the write model (invariants, aggregates) and the read model (joins, denormalised views, search) have different shapes and scaling needs.
- **Guarantees:** independent evolution and scaling of reads; read models can be rebuilt from events.
- **Does not guarantee:** read-your-writes — the read side is eventually consistent; the UI must tolerate lag or use the write side for confirmations.
- **Adds value:** reporting dashboards, list/search endpoints over many aggregates, multiple consumers with different views.
- **Unnecessary complexity:** CRUD entities where a JPA repository query serves both sides; projecting into a second table with the same schema.
- **Reference implementations:** `ddd-by-examples/all-things-cqrs` modules `in-one-class` → `with-application-events-immutable/src/main/java/io/dddbyexamples/cqrs/sink/WithdrawalProjection.java`; `ddd-by-examples/library` read model `src/main/java/io/pillopl/library/lending/patronprofile/`; `microservices-patterns/ftgo-application` `ftgo-order-history-service` (DynamoDB projection); `oskardudycz/EventSourcing.JVM` `samples/event-sourcing-esdb-simple/.../shoppingcarts/gettingbyid/ShoppingCartDetailsProjection.java` with checkpointed subscriptions.
- **Spring shape:** separate `command` and `query` packages (or Spring Modulith modules); projections as Kafka consumers writing to dedicated PostgreSQL tables or materialised views; version column on the read row to make projection updates idempotent (`UPDATE ... WHERE version < :eventVersion`).

## 3. Idempotent consumers

- **Problem:** at-least-once delivery (retries, rebalances, outbox re-sends) causes duplicate processing.
- **Guarantees:** a message with a given id produces its side effect at most once on state we control.
- **Does not guarantee:** idempotency of external calls unless the provider supports an idempotency key; protection against *different* messages with the same business meaning (that needs domain-level uniqueness).
- **Adds value:** every consumer that writes to a database or calls a non-idempotent API.
- **Unnecessary complexity:** naturally idempotent upserts keyed by aggregate id and version — then the version check *is* the dedupe.
- **Reference implementations:** `debezium/debezium-examples` `outbox/shipment-service/src/main/java/io/debezium/examples/outbox/shipment/messagelog/MessageLog.java` (`alreadyProcessed` + `processed` under `@Transactional(TxType.MANDATORY)`); `eventuate-tram-core` `received_messages (consumer_id, message_id)` table; `confluentinc/kafka-streams-examples` `src/test/java/io/confluent/examples/streams/EventDeduplicationLambdaIntegrationTest.java` (windowed state store).
- **Spring shape:** `processed_message(consumer_group, message_id, processed_at, primary key(consumer_group, message_id))`; in the `@KafkaListener` method, open a transaction, `INSERT ... ON CONFLICT DO NOTHING` and skip if zero rows affected, perform the side effect, commit, then acknowledge (manual ack mode). Carry `message_id` as a Kafka header produced from the outbox row id. Add a retention job for the dedupe table.

## 4. Durable workflows

- **Problem:** multi-step processes that span minutes to days, with timers, retries, human steps and compensation; in-memory orchestration dies with the process.
- **Guarantees:** progress survives restarts; each step is retried with policy; history is auditable.
- **Does not guarantee:** activity idempotency (activities are at-least-once in Temporal, Conductor and Zeebe alike); low operational cost.
- **Adds value:** onboarding/KYC flows, order fulfilment with external carriers, incident escalation chains, scheduled follow-ups.
- **Unnecessary complexity:** two-step request/response flows; anything finishing within one HTTP request.
- **Reference implementations:** `temporalio/samples-java` `core/src/main/java/io/temporal/samples/bookingsaga/TripBookingWorkflowImpl.java`, `moneytransfer/`, `polling/`, `fileprocessing/`; `conductor-oss/conductor` `core/` + `postgres-persistence/`; `camunda/camunda` (Zeebe); `keephq/keep` `keep/workflowmanager` (YAML-driven alert workflows).
- **Spring shape without an engine:** a `process_instance(id, type, state, version, next_run_at, attempts, payload jsonb)` table, state transitions as an explicit enum-driven state machine, a poller with `FOR UPDATE SKIP LOCKED` to pick due instances, and every transition emitting an outbox event. This is reviewable in a single repository. Reach for Temporal (Java SDK, `TestWorkflowEnvironment` for tests) only when timers and human tasks dominate.

## 5. Event sourcing

- **Problem:** state alone loses the history and the intent behind changes; audit and temporal queries become impossible.
- **Guarantees:** complete history; ability to rebuild any projection; natural optimistic concurrency via stream version.
- **Does not guarantee:** simple queries (needs projections), easy schema evolution (events are immutable; upcasting is required), GDPR erasure (needs crypto-shredding or tombstone strategies).
- **Adds value:** financial ledgers, booking/holding timelines, anything audited.
- **Unnecessary complexity:** reference data, user profiles, most CRUD.
- **Reference implementations:** `oskardudycz/EventSourcing.JVM` `workshops/build-your-own-event-store/solved/src/main/java/io/eventdriven/buildyourowneventstore/PgEventStore.java` (`streams` + `events` tables, `append_event(...)` function enforcing `expectedVersion`, `getEvents(... atStreamVersion, atTimestamp)`), `samples/events-versioning/`; `AxonIQ/AxonFramework` `eventsourcing/` module; `kurrent-io/KurrentDB-Client-Java`.
- **Spring shape:** `events(stream_id, version, type, data jsonb, metadata jsonb, created_at, unique(stream_id, version))`; append with expected version in one statement; aggregates rebuilt by folding events; projections fed by the outbox/CDC relay from the same table. Keep it to one or two aggregates where history is the product; store the rest as plain tables.

## 6. Saga orchestration vs choreography

- **Problem:** a business transaction spans services without a distributed transaction.
- **Guarantees:** eventual consistency via compensating actions; orchestration gives a single place to see the state; choreography gives loose coupling.
- **Does not guarantee:** isolation — intermediate states are visible (semantic locks or pending flags are needed); compensations may themselves fail and need retry/alerting.
- **Adds value:** 3+ participants, or any flow with money/inventory reservations.
- **Unnecessary complexity:** two services where one can simply retry an idempotent call; or where the "saga" is one service talking to itself.
- **Reference implementations:** orchestration — `microservices-patterns/ftgo-application` `ftgo-order-service/src/main/java/net/chrisrichardson/ftgo/orderservice/sagas/createorder/CreateOrderSaga.java` + `CreateOrderSagaState.java`; `eventuate-tram-sagas-examples-customers-and-orders` with command/reply contracts; `temporalio/samples-java` `bookingsaga`. Choreography vs saga side by side — `oskardudycz/EventSourcing.JVM` `samples/distributed-processes/.../hotelmanagement/choreography/` and `.../saga/`.
- **Spring shape:** orchestrator as a persisted state machine (see §4) that sends commands through the outbox and consumes replies idempotently; participants expose `reserve`/`release` pairs. Document the compensation matrix in the README and test it with a failing participant.

## 7. Backpressure

- **Problem:** producers or upstream topics outrun a consumer or a downstream dependency; unbounded queues turn into OOM or timeouts.
- **Guarantees:** bounded in-flight work; graceful slowdown instead of collapse.
- **Does not guarantee:** fairness between tenants or priority unless designed; low latency under saturation.
- **Adds value:** push delivery to third-party webhooks, bulk imports, fan-out projections.
- **Unnecessary complexity:** low-volume internal topics where consumer lag monitoring is enough.
- **Reference implementations:** `allegro/hermes` `hermes-consumers/src/main/java/pl/allegro/tech/hermes/consumers/consumer/rate/ConsumerRateLimiter.java`, `MaxRateSupervisor.java`, `InflightsPool.java`, `AdjustableSemaphore.java`, `idletime/ExponentiallyGrowingIdleTimeCalculator.java`; `confluentinc/parallel-consumer` `BrokerPollSystem.java` and `BrokerPollerBackpressureTest.java`; `LMAX-Exchange/disruptor` wait strategies; Project Reactor `onBackpressureBuffer` in `resilience4j-reactor`.
- **Spring shape:** bounded `max.poll.records` and `max.poll.interval.ms`; `Semaphore`/bulkhead around downstream calls; pause/resume the `KafkaListener` container when in-flight exceeds a threshold; expose consumer lag (`kafka_consumer_fetch_manager_records_lag_max`) and in-flight gauges; for HTTP ingress, Resilience4j `Bulkhead` plus 429/503 with `Retry-After`.

## 8. Concurrency control: optimistic, pessimistic, advisory locks

- **Problem:** concurrent updates to the same aggregate or shared resource.
- **Optimistic (`@Version`):** no blocking, detects conflicts at commit; retry on `OptimisticLockException`. Best for low contention and short transactions. Reference: `EventSourcing.JVM` `PgEventStore.appendEvents(expectedVersion)`; `gruelbox/transaction-outbox` `lock ... WHERE id = ? AND version = ?`.
- **Pessimistic (`SELECT ... FOR UPDATE`, `FOR NO KEY UPDATE`, `SKIP LOCKED`):** serialises writers; `SKIP LOCKED` is the standard queue-dispatch idiom for outbox and job tables. Reference: `transaction-outbox` dialect SQL; `thombergs/buckpal` `application/port/out/AccountLock.java` (port left as `NoOpAccountLock`, i.e. a known gap).
- **Advisory locks (`pg_advisory_xact_lock(key)`):** application-level mutex without a row (e.g. per-tenant import, singleton scheduler). ShedLock (used in `sivaprasadreddy` course) and Hazelcast CP locks are alternatives at different scales.
- **Does not guarantee:** cross-database or cross-service atomicity; protection from long-held locks blocking vacuum.
- **Spring shape:** `@Version` on aggregates by default; `@Lock(PESSIMISTIC_WRITE)` or native `FOR UPDATE SKIP LOCKED` for queues; `SELECT pg_try_advisory_xact_lock(hashtext(:key))` for singletons; test with two threads in a Testcontainers PostgreSQL and assert exactly one winner.

## 9. Caching and invalidation

- **Problem:** hot reads hitting PostgreSQL or slow external APIs.
- **Guarantees:** latency and load reduction for the cached fraction; TTL-bounded staleness.
- **Does not guarantee:** consistency across instances (local caches diverge), correctness after writes unless invalidation is explicit and ordered.
- **Adds value:** reference data, rate-limited third-party lookups (exchange rates in `piggymetrics` `ExchangeRatesClient`), read models queried far more than written.
- **Unnecessary complexity:** caching data that is written as often as read; caching inside the same transaction that writes it.
- **Reference implementations:** `spring-petclinic-microservices` `spring-petclinic-vets-service/src/main/java/.../system/CacheConfig.java` (Caffeine); `bucket4j-caffeine` and `bucket4j-postgresql` for distributed counters; `hazelcast` near-cache; `allegro/bigcache` (Go) for GC-friendly caching design notes.
- **Spring shape:** Spring Cache with Caffeine for per-instance read-through; event-driven invalidation by consuming the aggregate's own Kafka events (`@CacheEvict` in a listener); include the entity version in the cache value and ignore stale evictions; publish `cache_gets_total{result=hit|miss}`.

## 10. Graceful degradation

- **Problem:** a dependency slows down or fails; the system should shed the affected feature, not the whole request path.
- **Guarantees:** bounded latency (time limiter), fail-fast once a dependency is unhealthy (circuit breaker), reduced functionality with explicit signalling.
- **Does not guarantee:** correctness of fallbacks (a cached price may be wrong); recovery without retries.
- **Adds value:** read paths with optional enrichments, external providers with SLAs you do not control.
- **Unnecessary complexity:** wrapping local PostgreSQL calls in circuit breakers; fallbacks that return fake success for writes.
- **Reference implementations:** `resilience4j` modules `circuitbreaker`, `timelimiter`, `bulkhead`, `hedge`; `spring-petclinic-microservices` `spring-petclinic-api-gateway/.../FallbackController.java`; `piggymetrics` `StatisticsServiceClientFallback.java`; `allegro/hermes` readiness handling `hermes-frontend/.../readiness/DefaultReadinessChecker.java` and remote-DC speculative fallback.
- **Spring shape:** Resilience4j per named dependency with `@CircuitBreaker` + `@TimeLimiter`; fallbacks return a typed "degraded" result (e.g. `Optional.empty()` plus a `X-Degraded: pricing` header), never fabricated data; readiness probe fails when a required dependency is down, liveness does not; chaos test with Toxiproxy latency and assert the breaker opens and the endpoint still answers within the SLO.

## 11. Distributed tracing

- **Problem:** a request fans out across services and Kafka hops; logs alone cannot reconstruct causality.
- **Guarantees:** correlation of spans across HTTP and Kafka when context is propagated in headers (`traceparent`); latency breakdown per hop.
- **Does not guarantee:** sampling-free completeness (head sampling drops traces), or business-level causality unless you add domain attributes.
- **Adds value:** any asynchronous flow; outbox relays and consumers are the usual place where context is lost.
- **Unnecessary complexity:** a single-process monolith with no async boundaries — structured logs with a request id are enough.
- **Reference implementations:** `debezium/debezium-examples` `outbox/shipment-service/.../facade/KafkaEventConsumer.java` (manual spans around consume, handler and ack); `temporalio/samples-java` `tracing/`; `sivaprasadreddy` course `deployment/docker-compose/tempo/tempo.yml`; `open-telemetry/opentelemetry-java-instrumentation`.
- **Spring shape:** Micrometer Tracing with the OTel bridge; store `traceparent` in the outbox `headers` column and re-inject it when the relay produces, so the consumer span links to the originating request; export to Tempo/Jaeger via OTLP; include `trace_id` in JSON logs.

## 12. Schema evolution

- **Problem:** events and APIs change while old messages remain in topics and old consumers remain deployed.
- **Guarantees (with a registry and compatibility rules):** producers cannot publish incompatible schemas; consumers can read older/newer versions within the compatibility mode.
- **Does not guarantee:** semantic compatibility (a renamed field with the same type passes); correctness of upcasters.
- **Adds value:** any topic consumed by more than one deployable or retained beyond a deploy cycle.
- **Unnecessary complexity:** a registry for a single-producer, single-consumer topic inside one repository — a versioned JSON envelope with a `schema_version` field and consumer-side tolerance may suffice, provided the rule is documented.
- **Reference implementations:** `allegro/hermes` `hermes-schema/` (Avro schema repository integration) and `allegro/json-avro-converter`; `oskardudycz/EventSourcing.JVM` `samples/events-versioning/`; `confluentinc/schema-registry` (Confluent Community License) vs `Apicurio/apicurio-registry` (Apache-2.0); `confluentinc/kafka-streams-examples` `JsonToAvroExample.java`.
- **Spring shape:** Avro or Protobuf with Apicurio registry in Docker Compose, `BACKWARD` compatibility enforced in CI with a schema-check step; or JSON with `spring-kafka` `JsonDeserializer` ignoring unknown fields plus an explicit `eventVersion` header and upcaster chain; Flyway for PostgreSQL with expand/contract migrations; a test that replays a fixture of version-1 events through the current consumer.

## 13. Contract testing

- **Problem:** integration tests with all services running are slow and flaky; unit tests of clients mock the provider's behaviour incorrectly.
- **Guarantees:** the consumer's expectations are verified against the provider's actual implementation for HTTP and messaging, independently deployable.
- **Does not guarantee:** end-to-end business correctness, performance, or that the contract itself is sensible.
- **Adds value:** more than one deployable and more than one developer; asynchronous message schemas where the consumer is not the schema owner.
- **Unnecessary complexity:** a modular monolith with a single deployable — ArchUnit boundaries and in-process tests do the job.
- **Reference implementations:** `eventuate-tram-sagas-examples-customers-and-orders` `customer-service/customer-service-credit-reservation-api/src/contractTest/resources/contracts/order-service/commands/reserveCreditCommand.groovy` and `replies/reserveCreditReply.groovy`; `microservices-patterns/ftgo-application` `ftgo-*-service-contracts/src/main/resources/contracts/**.groovy`; `pact-foundation/pact-jvm` (message pacts); `microcks/microcks` (AsyncAPI-based mocks). Note that Spring Cloud Contract now lives in `spring-attic` (archived), which should inform tool choice.
- **Spring shape:** Pact JVM consumer tests generate pacts for REST clients and Kafka message consumers; provider verification runs in the provider's CI with Spring's `MockMvc`/`WebTestClient`; publish pacts to a broker or commit them; AsyncAPI generated by `springwolf` serves as human-readable contract documentation.

## 14. Load testing

- **Problem:** performance claims without measurement; regressions discovered in production.
- **Guarantees:** reproducible numbers for a defined scenario and environment; thresholds that fail CI.
- **Does not guarantee:** production parity (laptop Docker Compose is not a cluster); meaningful averages — report percentiles.
- **Adds value:** any throughput or latency statement in the README; capacity decisions for consumer parallelism and connection pools.
- **Unnecessary complexity:** micro-optimising before a baseline exists; benchmarking framework overhead instead of the domain path.
- **Reference implementations:** `allegro/hermes` `hermes-benchmark/src/jmh/java/.../HermesServerBenchmark.java` with `.github/workflows/benchmarks.yml` and `benchmark-report-compare.py`; `GoogleCloudPlatform/microservices-demo` `src/loadgenerator/locustfile.py`; `spring-petclinic-microservices` `spring-petclinic-api-gateway/src/test/jmeter/petclinic_test_plan.jmx`; `LMAX-Exchange/disruptor` `jmh/` and `perftest/`; `OpenHFT/Chronicle-Queue` JLBH benchmarks; `bucket4j-benchmarks`; `confluentinc/parallel-consumer` `LoadTest.java`.
- **Spring shape:** k6 or Gatling scripts committed under `load/` with scenario, VU ramp, and thresholds (`p95 < 300ms`, `error_rate < 1%`); a `docker compose --profile load` target; JMH for hot in-process paths (serialisation, matching logic); results stored as Markdown with environment description, warm-up, duration and percentiles; Grafana dashboard JSON committed next to the Prometheus config, as in `spring-petclinic-microservices/docker/grafana/dashboards/`.

## 15. Pattern selection heuristics for the portfolio

| Situation | Use | Skip |
|-----------|-----|------|
| Any event leaving the service | Outbox (Spring Modulith registry or own table) + idempotent consumer | Publish-after-commit |
| Flow with reservations across 2+ aggregates/services | Orchestrated saga as persisted state machine | Choreography without a visible process state |
| Hours-to-days process with timers/human steps | Temporal or persisted scheduler table | Threads and `@Scheduled` with in-memory state |
| Audit-critical aggregate | Event sourcing for that aggregate only | Event sourcing everywhere |
| Hot read endpoints | Caffeine + event-driven eviction | Distributed cache cluster |
| Performance statements | Committed k6/Gatling + JMH with percentiles | Numbers in README without scripts |
| Multi-deployable repo | Pact or Spring Cloud Contract successor + ArchUnit | Full-stack E2E as the only integration test |
