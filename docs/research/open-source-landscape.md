# Open-Source Landscape: Java Backend, Event-Driven and Workflow Systems

Verification date: 2026-10-23. All repository metadata (stars, last push, license, primary language, archived flag) was retrieved on that date through the authenticated GitHub REST API (`gh api repos/OWNER/REPO`). Code structure claims come from inspecting Git trees and file contents (`git/trees/HEAD?recursive=1`, `contents/PATH`), not from READMEs. Licenses marked "NOASSERTION" are what GitHub reports when the license file is non-standard; the actual terms are noted where they were checked.

Scope: input for a flagship Java 21 / Spring Boot / PostgreSQL / Kafka portfolio repository targeted at Middle+/Senior roles. The product concept is not fixed, so the analysis is general. No claim is made that any repository "got anyone hired"; the goal is to learn what strong public engineering looks like and where common samples fall short.

## 1. Summary table

| # | Project | Category | Stars | Last push | License | Lang | Notes |
|---|---------|----------|------:|-----------|---------|------|-------|
| 1 | spring-petclinic/spring-petclinic-microservices | Spring reference app | 2,224 | 2026-10-07 | Apache-2.0 | Java | Spring Boot 4.0.1, Java 17, chaos-monkey, Grafana |
| 2 | sqshq/piggymetrics | Spring reference app | 13,969 | 2024-05-17 | MIT | Java | Netflix OSS era, MongoDB |
| 3 | GoogleCloudPlatform/microservices-demo | Polyglot reference app | 21,079 | 2026-10-22 | Apache-2.0 | Go | gRPC, Locust load generator |
| 4 | ddd-by-examples/library | Modular monolith / DDD | 5,882 | 2026-09-17 | MIT | Java | Spock, ArchUnit, store-and-forward events |
| 5 | ddd-by-examples/all-things-cqrs | CQRS variants | 603 | 2019-04-04 | none | Java | Stale but instructive |
| 6 | spring-projects/spring-modulith | Modular monolith framework | 1,201 | 2026-10-23 | Apache-2.0 | Java | Event publication registry (outbox-like) |
| 7 | eventuate-tram/eventuate-tram-sagas-examples-customers-and-orders | Saga orchestration | 582 | 2026-10-22 | NOASSERTION | Java | Contract, component, E2E test tiers |
| 8 | eventuate-tram/eventuate-tram-core | Outbox / messaging | 1,179 | 2026-10-22 | NOASSERTION | Java | `message` + `received_messages` tables |
| 9 | microservices-patterns/ftgo-application | Saga / CQRS book code | 3,777 | 2026-03-13 | NOASSERTION | Java | Spring Cloud Contract, Cucumber |
| 10 | debezium/debezium | CDC | 13,263 | 2026-10-23 | Apache-2.0 | Java | Outbox EventRouter SMT, microbenchmarks |
| 11 | debezium/debezium-examples | CDC examples | 1,686 | 2026-10-18 | Apache-2.0 | JS* | `outbox/` Quarkus services with message log |
| 12 | gruelbox/transaction-outbox | Outbox library | 344 | 2026-10-22 | Apache-2.0 | Java | Multi-DB acceptance tests |
| 13 | sivaprasadreddy/spring-boot-microservices-course | Spring course app | 223 | 2026-08-01 | Apache-2.0 | Java | Outbox table + ShedLock job, Testcontainers |
| 14 | temporalio/temporal (+ samples-java) | Durable workflows | 23,931 / 263 | 2026-10-23 | MIT / custom | Go / Java | Saga sample with compensations |
| 15 | conductor-oss/conductor | Workflow orchestration | 32,370 | 2026-10-23 | Apache-2.0 | Java | Netflix/conductor archived 2023-12 |
| 16 | camunda/camunda (Zeebe) | BPMN orchestration | 4,337 | 2026-10-23 | none (mixed) | Java | camunda-bpm-platform archived |
| 17 | cadence-workflow/cadence | Durable workflows | 9,510 | 2026-10-22 | Apache-2.0 | Go | Temporal's ancestor |
| 18 | AxonIQ/AxonFramework | CQRS / event sourcing | 3,625 | 2026-10-22 | Apache-2.0 | Java | Axon 5 in progress |
| 19 | kurrent-io/KurrentDB-Client-Java | Event store client | 74 | 2026-09-14 | Apache-2.0 | Java | Formerly EventStoreDB-Client-Java |
| 20 | oskardudycz/EventSourcing.JVM | Event sourcing samples | 327 | 2026-09-17 | CC-BY-SA-4.0 | Java | PostgreSQL event store workshop |
| 21 | keephq/keep | Incident / alert mgmt | 12,431 | 2026-10-22 | custom (ee/) | Python | Workflow engine for alerts |
| 22 | grafana-cold-storage/oncall | On-call | 3,881 | 2026-03-24 | AGPL-3.0 | Python | Archived (was grafana/oncall) |
| 23 | linkedin/oncall | On-call scheduling | 1,258 | 2025-08-20 | BSD-2-Clause | Python | pytest e2e suite |
| 24 | Netflix/dispatch | Incident management | 6,485 | 2025-09-03 | Apache-2.0 | Python | Archived |
| 25 | apache/flink | Stream processing | 26,421 | 2026-10-23 | Apache-2.0 | Java | Checkpointing, exactly-once sinks |
| 26 | confluentinc/kafka-streams-examples | Streams examples | 180 | 2026-10-22 | Apache-2.0 | Java | Dedup, corrupted input tests |
| 27 | confluentinc/parallel-consumer | Kafka consumer lib | 302 | 2026-10-22 | Apache-2.0 | Java | Backpressure, EoS tests |
| 28 | aeron-io/aeron | Low-latency messaging | 8,931 | 2026-10-23 | Apache-2.0 | Java | Cluster, archive, throughput samples |
| 29 | LMAX-Exchange/disruptor | Ring buffer | 18,490 | 2025-04-02 | Apache-2.0 | Java | jmh, jcstress, perftest |
| 30 | OpenHFT/Chronicle-Queue | Persisted queue | 3,823 | 2026-10-18 | Apache-2.0 | Java | JLBH latency benchmarks |
| 31 | hazelcast/hazelcast | Distributed state | 6,624 | 2026-10-22 | Apache-2.0 / HCL | Java | ArchUnit rules module |
| 32 | apache/ignite | Distributed state | 5,087 | 2026-10-23 | Apache-2.0 | Java | `benchmarks/`, ducktests |
| 33 | resilience4j/resilience4j | Resilience | 10,793 | 2026-10-07 | Apache-2.0 | Java | spring-boot3 and spring-boot4 modules |
| 34 | bucket4j/bucket4j | Rate limiting | 2,817 | 2026-10-04 | Apache-2.0 | Java | PostgreSQL backend, lincheck tests |
| 35 | allegro/hermes | Kafka pub/sub broker (PL) | 872 | 2026-10-22 | Apache-2.0 | Java | JMH benchmarks in CI, adaptive rate limiting |
| 36 | Allegro tech blog + allegro/envoy-control, bigcache | PL case studies | 111 / 8,173 | 2026-03 / 2026-10 | Apache-2.0 | Kotlin / Go | envoy-control archived 2026-03 |
| 37 | piomin/sample-spring-microservices-new | Spring Cloud demo (PL) | 1,393 | 2026-10-03 | none | Java | Config/Eureka/Gateway |
| 38 | thombergs/buckpal | Hexagonal architecture | 2,558 | 2024-06-03 | none | Java | ArchUnit DSL |
| 39 | citerus/dddsample-core | Classic DDD | 5,293 | 2025-06-02 | MIT | Java | Cargo domain |
| 40 | ewolff/microservice-kafka | Kafka microservices | 630 | 2024-08-01 | Apache-2.0 | Java | Embedded Kafka tests |
| 41 | kgrzybek/modular-monolith-with-ddd | Modular monolith (PL) | 14,091 | 2024-06-04 | MIT | C# | Outbox/inbox reference, not JVM |
| 42 | Tooling cluster: testcontainers-java, Shopify/toxiproxy, grafana/k6, gatling, pact-jvm, microcks, springwolf | Test / load / contract | 8,752 / 12,451 / 32,016 / 6,954 / 1,148 / 2,069 / 352 | 2026-10 | MIT / MIT / AGPL / Apache / Apache / Apache / Apache | mixed | spring-cloud-contract moved to spring-attic |

\* GitHub reports JavaScript as primary language for debezium-examples because of UI assets; the outbox example is Java (Quarkus).

## 2. Per-project assessment

### 2.1 Spring reference applications

**spring-petclinic/spring-petclinic-microservices** — https://github.com/spring-petclinic/spring-petclinic-microservices
- Core problem: showcase Spring Cloud on a tiny veterinary domain (owners, pets, vets, visits).
- Architecture: config server, Eureka discovery, Spring Cloud Gateway, Spring Boot Admin, three CRUD services plus a `spring-petclinic-genai-service` (Spring AI). Root `pom.xml` pins Spring Boot 4.0.1, Spring Cloud 2025.1.0, `java.version` 17.
- Failure handling: Resilience4j circuit breaker only in the gateway (`spring-petclinic-api-gateway/.../FallbackController.java`); `scripts/chaos/*.json` drive chaos-monkey-spring-boot attacks (latency, exceptions, kill, memory).
- Observability: `docker/prometheus`, `docker/grafana/dashboards/grafana-petclinic-dashboard.json`, datasource-micrometer.
- Testing: one web-layer test per service (`PetResourceTest`, `VetResourceTest`, `VisitResourceTest`), a gateway client integration test, a JMeter plan (`src/test/jmeter/petclinic_test_plan.jmx`). No persistence, contract or failure-injection tests.
- Performance evidence: none published; JMeter plan exists but no results.
- Weaknesses: domain is CRUD only; services share no business workflow; chaos scripts are not tied to assertions.
- Lesson: excellent as an infrastructure checklist (chaos, dashboards, admin), weak as a demonstration of distributed-data correctness.

**sqshq/piggymetrics** — https://github.com/sqshq/piggymetrics
- Core problem: personal finance tracker used to demonstrate Spring Cloud / Netflix OSS.
- Architecture: account, statistics, notification services, OAuth2 `auth-service`, gateway (Zuul), config, registry (Eureka), Turbine stream monitoring; MongoDB per service; Feign clients with fallbacks (`StatisticsServiceClientFallback.java`).
- Testing: unit and Mongo repository tests in every service; `.travis.yml` CI.
- Status: last push 2024-05-17; stack is Spring Boot 2.x era (Zuul, Hystrix, Turbine are all retired upstream).
- Strength: the only early sample with per-service security config and a scheduled notification job (`NotificationServiceImpl`).
- Weaknesses: no messaging, no transactional guarantees across services, synchronous fan-out.
- Lesson: stars reflect 2015–2018 relevance; a 2026 portfolio must not copy this stack.

**GoogleCloudPlatform/microservices-demo (Online Boutique)** — https://github.com/GoogleCloudPlatform/microservices-demo
- Core problem: Kubernetes/GKE showcase for a polyglot shop (Go, Python, Java `adservice`, Node, C#).
- Architecture: gRPC contracts in `protos/demo.proto`; Kustomize components; `src/loadgenerator/locustfile.py` for constant synthetic load.
- Testing: sparse unit tests (`product_catalog_test.go`, `shippingservice_test.go`); CI focuses on deployment (`.github/workflows/deploy-pr.yaml`, terraform validate, helm chart CI).
- Failure handling: none at application level; relies on platform (Istio variants).
- Lesson: shows how a load generator becomes part of the deliverable, and how a schema-first (protobuf) API contract looks; not a model for data consistency.

**piomin/sample-spring-microservices-new** — https://github.com/piomin/sample-spring-microservices-new (Polish author, widely read blog)
- Config, discovery, gateway plus employee/department/organization services; CircleCI; `docker-compose.yml`.
- Tests are smoke tests per service (`DepartmentAppTests`, `EmployeeRepositoryTest`).
- No persistence beyond in-memory repositories; no messaging; no license file.
- Lesson: a good "how to wire Spring Cloud" tutorial, but has the shape that hiring reviewers see most often.

**sivaprasadreddy/spring-boot-microservices-course** — https://github.com/sivaprasadreddy/spring-boot-microservices-course
- Bookstore with catalog, orders, notifications, gateway, webapp; Keycloak realm in `deployment/docker-compose/realm-config`; Prometheus, Promtail, Tempo compose stack; `Taskfile.yml`.
- Transactional guarantees: polling outbox — `order-service/.../domain/OrderEventEntity.java` persisted with the order, `jobs/OrderEventsPublishingJob.java` publishes under `@SchedulerLock` (ShedLock) so only one instance drains the table. Consumer side stores `OrderEventEntity` in notification-service for duplicate detection.
- Testing: Testcontainers (`AbstractIT`, `ContainersConfig`), controller and repository tests, `OrderEventHandlerTests`.
- Weaknesses: outbox publishes without ordering/partition-key reasoning; no failure tests.
- Lesson: one of the few course-style repos that implements the outbox honestly and keeps the infra small.

**ewolff/microservice-kafka** — https://github.com/ewolff/microservice-kafka
- Order, invoicing, shipping services communicating through Kafka; custom `InvoiceDeserializer`, `OrderKafkaListener`; tests with embedded Kafka (`OrderKafkaTest`, `InvoiceKafkaTest`).
- No outbox: order is saved and then published, so dual-write is possible. Last push 2024-08.
- Lesson: a minimal, readable baseline of what *not* to stop at (publish-after-commit without an outbox).

### 2.2 Modular monoliths and DDD

**ddd-by-examples/library** — https://github.com/ddd-by-examples/library (Polish authors)
- Core problem: library lending domain modelled with DDD, hexagonal architecture and functional style (Vavr `Either`, immutable `Patron`, policies as functions).
- Architecture: single deployable, bounded contexts `catalogue` and `lending` (sub-packages `patron`, `book`, `patronprofile` read model); events via `commons/events`.
- Transactional outbox in miniature: `StoreAndForwardDomainEventPublisher.java` saves events to `EventsStorage` and publishes them on a `@Scheduled(fixedRate = 3000)` `@Transactional` loop; `MeteredDomainEventPublisher` adds metrics.
- Testing: Spock specifications in `src/test/groovy`, `ModularArchitectureTest.java` and `LendingHexagonalArchitectureTest.java` (ArchUnit) enforce boundaries.
- Documentation: README explains event storming, context map and the C4-style decisions.
- Weaknesses: `EventsStorage.published()` marks a batch after publishing, so a crash between publish and mark re-publishes (at-least-once, undocumented); no Kafka, no multi-instance scheduler lock.
- Lesson: the best JVM example of domain modelling plus enforced architecture tests; the outbox is pedagogical rather than production-grade.

**ddd-by-examples/all-things-cqrs** — https://github.com/ddd-by-examples/all-things-cqrs
- Six-plus Maven modules implementing the same credit-card withdrawal with increasing separation: `in-one-class`, `explicit-with-entity`, `explicit-with-dto`, `with-application-events`, `with-application-events-immutable` (projection in `sink/WithdrawalProjection.java`), and broker/CDC variants.
- Last push 2019; dependencies are old, but the progression itself is the asset.
- Lesson: CQRS is a spectrum; a portfolio should justify where on the spectrum it sits.

**spring-projects/spring-modulith** — https://github.com/spring-projects/spring-modulith
- Core problem: enforce module boundaries in a monolith and make application events reliable.
- Event publication registry: `spring-modulith-events-jdbc/.../schemas/v2/schema-postgresql.sql` creates `event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date, status, completion_attempts, last_resubmission_date)`; `JdbcEventPublicationRepository(V2).java` persists per-listener publications in the same transaction, and `spring-modulith-events-kafka/KafkaEventExternalizerConfiguration.java` externalizes `@Externalized` events.
- This is an outbox in all but name: at-least-once, resubmission of incomplete publications, archive tables for completed entries.
- Weaknesses: externalization order and partition keys need explicit configuration; completion is per listener, so a Kafka externalizer and an internal listener are tracked separately.
- Lesson: for a Spring Boot + PostgreSQL + Kafka portfolio, this is the most idiomatic and least hand-rolled outbox available; using it and explaining its guarantees is stronger than reimplementing.

**thombergs/buckpal** — https://github.com/thombergs/buckpal
- Hexagonal money transfer: `application/port/in|out`, `adapter/in/web`, `adapter/out/persistence`; `AccountLock` port with a `NoOpAccountLock` adapter (an honest placeholder for pessimistic locking).
- Tests: `DependencyRuleTests.java` plus a small ArchUnit DSL in `src/test/java/.../archunit/HexagonalArchitecture.java`; `SendMoneySystemTest`.
- Last push 2024-06; no license file.
- Lesson: shows how to make architecture rules executable; the `NoOpAccountLock` is a reminder that concurrency control is often left as an exercise.

**citerus/dddsample-core** — https://github.com/citerus/dddsample-core
- Original DDD cargo shipping sample (Evans/Citerus); MIT; last push 2025-06. Aggregates, repositories, specifications; no messaging.
- Lesson: still the clearest example of aggregate boundaries and value objects; useful vocabulary for a domain document.

**kgrzybek/modular-monolith-with-ddd** — https://github.com/kgrzybek/modular-monolith-with-ddd (Polish author, C#)
- 14k stars; `Modules/`, `BuildingBlocks/`, `Tests/` with architecture, integration and system test projects; outbox and inbox tables processed by scheduled jobs; extensive README with decision log.
- Not JVM, but the README structure (motivation, architecture, decision log, limits) is the reference for documentation quality.

### 2.3 Transactional outbox and CDC

**gruelbox/transaction-outbox** — https://github.com/gruelbox/transaction-outbox
- Library that records method invocations in a `TXNO_OUTBOX` table inside the caller's transaction and replays them.
- `DefaultDialect.java`: `selectBatch` with `LIMIT {{batchSize}}`, `lock` as `SELECT ... WHERE id = ? AND version = ? FOR UPDATE` (optimistic version check), `fetchNextSequence` from `TXNO_SEQUENCE ... FOR UPDATE` for ordered topics; `DefaultPersistor.java` tracks `attempts`, `blocked`, `processed`, `nextAttemptTime`, `uniqueRequestId`.
- Testing: `transactionoutbox-acceptance` runs the suite against PostgreSQL 11–16, MySQL 5/8, Oracle 18/21, MSSQL 2019/2022 and H2 via Testcontainers; migration tests.
- Modules: core, jackson, jooq, guice (and framework integrations).
- Weaknesses: at 344 stars it is niche; invocation-serialization approach couples outbox entries to Java method signatures (schema evolution risk).
- Lesson: shows what "production-grade outbox" means — dialect-specific SQL, blocking after N attempts, per-topic ordering, cross-database acceptance tests.

**eventuate-tram/eventuate-tram-core** — https://github.com/eventuate-tram/eventuate-tram-core
- Messaging framework behind Chris Richardson's patterns. Embedded schema (`eventuate-tram-in-memory/.../eventuate-tram-embedded-schema.sql`) defines `message (id, destination, headers, payload, message_partition, creation_time)` and `received_messages (consumer_id, message_id)` — the canonical pair of outbox plus idempotent-consumer tables.
- Relay is a separate CDC service (`eventuate-cdc`, polling or binlog/WAL) rather than in-process.
- Weaknesses: license is NOASSERTION; the ecosystem is tightly coupled to its own framework classes.
- Lesson: the two-table design is the baseline a reviewer expects in a Kafka + PostgreSQL project.

**debezium/debezium** — https://github.com/debezium/debezium
- Log-based CDC for PostgreSQL (logical decoding), MySQL, MongoDB, Oracle, SQL Server; `debezium-server` for broker-less deployment; outbox routing in `debezium-connect-plugins/src/main/java/io/debezium/transforms/outbox/EventRouter.java`; Quarkus outbox extension modules `debezium-quarkus-outbox*`.
- Performance evidence: `debezium-microbenchmark`, `debezium-microbenchmark-engine`, `debezium-e2e-benchmark` modules.
- Lesson: when the relay must be reliable and ordered per aggregate, WAL-based CDC with the EventRouter SMT is the industry answer; a portfolio should at least explain why it chose polling or CDC.

**debezium/debezium-examples (outbox)** — https://github.com/debezium/debezium-examples/tree/main/outbox
- Quarkus `order-service` writes to an outbox table; Debezium PostgreSQL connector (`outbox/register-postgres.json`) routes to Kafka; `shipment-service` consumes.
- Idempotent consumer: `shipment-service/.../messagelog/MessageLog.java` persists `ConsumedMessage(eventId)` under `@Transactional(TxType.MANDATORY)` and checks `alreadyProcessed` within the same transaction as the business write; `KafkaEventConsumer.java` reads `id`/`eventType` headers, acknowledges manually and wraps each step in OpenTelemetry spans.
- Weaknesses: no automated tests in the example; Quarkus rather than Spring.
- Lesson: the simplest complete, end-to-end outbox + idempotent-consumer + tracing demonstration in the JVM ecosystem.

### 2.4 Sagas and workflow orchestration

**eventuate-tram-sagas-examples-customers-and-orders** — https://github.com/eventuate-tram/eventuate-tram-sagas-examples-customers-and-orders
- Orchestration-based saga: order service orchestrates credit reservation in the customer service via command/reply channels.
- Code structure splits each service into `-api`, `-domain`, `-persistence`, `-restapi`, `-main` Gradle modules; `buildSrc` defines `ComponentTestsPlugin`, `IntegrationTestsPlugin`, `EndToEndTestsPlugin`.
- Contract tests: Spring Cloud Contract Groovy DSL for messaging (`contracts/order-service/commands/reserveCreditCommand.groovy`, `replies/reserveCreditReply.groovy`) and HTTP (`contracts/apigateway/createCustomer.groovy`).
- End-to-end: `ApplicationUnderTestUsingDockerCompose`, `...UsingKind`, `...UsingTestContainers`, `...UsingStubbed` variants.
- Weaknesses: heavy framework coupling; business logic is thin.
- Lesson: the clearest public example of a four-tier test pyramid (unit, integration, component, end-to-end) for messaging services.

**microservices-patterns/ftgo-application** — https://github.com/microservices-patterns/ftgo-application
- Food-to-go domain from the *Microservices Patterns* book: `CreateOrderSaga`, `CancelOrderSaga`, `ReviseOrderSaga` with explicit `*SagaState` classes; CQRS order history on DynamoDB; gRPC and GraphQL gateways; AWS Lambda restaurant service variant.
- Testing: Spring Cloud Contract contracts per service (`ftgo-*-service-contracts`), Cucumber component tests (`create-revise-cancel.feature`), JPA integration tests, `src/attic` of superseded tests (honest history).
- Weaknesses: last push 2026-03 but dependency drift is visible; NOASSERTION license; many moving parts for a reader.
- Lesson: the reference for naming compensating steps and separating saga state from domain state.

**temporalio/temporal and temporalio/samples-java** — https://github.com/temporalio/temporal, https://github.com/temporalio/samples-java
- Durable execution engine (Go server, MIT) with Java SDK samples. `core/src/main/java/io/temporal/samples/bookingsaga/TripBookingWorkflowImpl.java` uses the SDK `Saga` helper to register compensations; `moneytransfer`, `moneybatch`, `fileprocessing`, `polling`, `interceptorreplaytest`, `tracing`, `metrics`, `nexus` samples; each has a mirrored test package using the SDK's test environment.
- Guarantees: workflow code is replayed deterministically; activities are at-least-once and must be idempotent — the samples do not hide this.
- Weaknesses: a Temporal cluster is heavyweight for a portfolio; samples are intentionally tiny.
- Lesson: if a portfolio has a long-running process (hours to days, human steps, timers), Temporal removes hand-written state machines; otherwise a PostgreSQL-backed state machine plus outbox is easier to review.

**conductor-oss/conductor** — https://github.com/conductor-oss/conductor
- Community continuation of Netflix Conductor (original archived 2023-12-22). JSON-defined workflows, worker polling model; persistence modules for PostgreSQL, MySQL, Redis, Cassandra, Elasticsearch/OpenSearch; `test-harness`, `e2e`, gRPC server/client, UI.
- Lesson: shows a polyglot worker model (HTTP/gRPC task polling), and that a project surviving an upstream archive needs a clear governance story.

**camunda/camunda (Zeebe, Operate, Tasklist)** — https://github.com/camunda/camunda
- BPMN engine with a partitioned, replicated log (Raft); the legacy `camunda/camunda-bpm-platform` (Camunda 7 CE) was archived 2025-11. Licensing is mixed (GitHub reports none); evaluate before embedding.
- Lesson: BPMN gives a visual, auditable process model that non-engineers can read; the cost is operational weight and license diligence.

**cadence-workflow/cadence** — https://github.com/cadence-workflow/cadence
- Uber's durable workflow engine (Go) from which Temporal forked. Still active (push 2026-10-22). Less relevant for JVM portfolios than Temporal, but documents the same event-history replay model.

### 2.5 CQRS and event sourcing

**AxonIQ/AxonFramework** — https://github.com/AxonIQ/AxonFramework
- Command bus, event bus, aggregates, sagas, tracking event processors; modules `messaging`, `modelling`, `eventsourcing`, `integrationtests`; an `axon-5` directory shows the next major version in development.
- Lesson: mature CQRS/ES in Java but opinionated; a portfolio that uses it must show it understands what the framework hides (token stores, replay, upcasting).

**kurrent-io/KurrentDB-Client-Java** — https://github.com/kurrent-io/KurrentDB-Client-Java
- Official client for KurrentDB (renamed EventStoreDB); 74 stars; gRPC-based; last push 2026-09-14. Shows the rebrand risk of depending on a niche store.

**oskardudycz/EventSourcing.JVM** — https://github.com/oskardudycz/EventSourcing.JVM (Polish author)
- Samples (`event-sourcing-esdb-simple`, `-aggregates`, `distributed-processes`, `uniqueness`, `events-versioning`) and workshops. `workshops/build-your-own-event-store/solved/.../PgEventStore.java` implements a PostgreSQL event store with `streams` and `events` tables and an `append_event(...)` function that enforces `expectedVersion` (optimistic concurrency) in a single statement.
- `distributed-processes` compares choreography and saga deciders for a hotel group checkout; subscription checkpoints (`EventStoreDBSubscriptionCheckpointRepository`) show how projections resume.
- Tests exist for facades, sagas and workers (`OrderSagaTests`, `PaymentTimeoutWorkerTests`).
- Lesson: the most instructive JVM resource on honest event sourcing trade-offs (versioning, uniqueness, process managers), and a PostgreSQL-native event store that fits the target stack.

### 2.6 Reliability and incident tooling

**keephq/keep** — https://github.com/keephq/keep
- Python alert-management platform: `keep/providers` (integrations), `keep/rulesengine`, `keep/workflowmanager` (YAML workflows), `keep/throttles`, `keep/topologies`; separate `keep-ui`; `ee/` directory under a separate license.
- Lesson: alert deduplication, correlation and throttling are a domain with real state and timing problems — a credible non-CRUD target for a Java backend.

**grafana-cold-storage/oncall (formerly grafana/oncall)** — https://github.com/grafana-cold-storage/oncall
- Archived (last push 2026-03-24), AGPL-3.0, Django. Escalation chains, schedules, integrations. The archive status is itself a lesson about depending on vendor OSS.

**linkedin/oncall** — https://github.com/linkedin/oncall
- On-call calendar/scheduling API (Falcon + MySQL); `e2e/` pytest suite (`test_schedules.py`, `test_override.py`, `test_roster_suggest.py`) exercises the HTTP API end-to-end. Scheduling fairness and overrides are concrete, testable rules.

**Netflix/dispatch** — https://github.com/Netflix/dispatch
- Archived incident-management platform (FastAPI); `src/dispatch` has `incident`, `case`, `signal`, `workflow`, `plugins`, `cost_model` modules. Shows a plug-in model and incident cost accounting as domain concepts.

### 2.7 Streaming and real-time analytics

**apache/flink** — https://github.com/apache/flink
- Stateful stream processing with checkpoint barriers and two-phase-commit sinks (exactly-once *within* the pipeline when sources are replayable and sinks are transactional). 26k stars; Java/Scala.
- Lesson: when a portfolio needs windowed aggregates or joins across topics, Flink or Kafka Streams is the appropriate tool; a hand-rolled consumer with in-memory maps is not.

**confluentinc/kafka-streams-examples** — https://github.com/confluentinc/kafka-streams-examples
- Topologies (`WordCountLambdaExample`, `SessionWindowsExample`, `GlobalKTablesExample`, `AnomalyDetectionLambdaExample`); tests show patterns a portfolio can reuse: `EventDeduplicationLambdaIntegrationTest`, `HandlingCorruptedInputRecordsIntegrationTest`, `ApplicationResetIntegrationTest`, `StateStoresInTheDSLIntegrationTest`.
- Lesson: deduplication with a windowed state store and poison-pill handling are tested patterns, not folklore.

**confluentinc/parallel-consumer** — https://github.com/confluentinc/parallel-consumer
- Per-key parallelism beyond partition count, client-side queueing, offset encoding for out-of-order acks. Integration tests include `BrokerPollerBackpressureTest`, `TransactionAndCommitModeTest`, `RebalanceEoSDeadlockTest`, `LoadTest`, `VeryLargeMessageVolumeTest`.
- Lesson: shows how to test backpressure and rebalance behaviour explicitly, and why "exactly once" needs transaction tests.

### 2.8 High-throughput and low-latency Java

**aeron-io/aeron** — https://github.com/aeron-io/aeron
- UDP/IPC messaging, `aeron-archive`, `aeron-cluster` (Raft); `aeron-samples` contains `EmbeddedThroughput`, `EmbeddedPingPong`, `Ping/Pong` latency tools; `aeron-system-tests` for multi-process behaviour.
- Lesson: performance claims are backed by runnable harnesses in the repo.

**LMAX-Exchange/disruptor** — https://github.com/LMAX-Exchange/disruptor
- Ring-buffer inter-thread messaging; dedicated `jmh/`, `jcstress/` (concurrency correctness) and `perftest/` source sets.
- Lesson: correctness under concurrency (jcstress) and throughput (JMH) are separate disciplines with separate tooling.

**OpenHFT/Chronicle-Queue** — https://github.com/OpenHFT/Chronicle-Queue
- Memory-mapped persisted queue; `src/test/java/.../bench/*JLBHBenchmark.java`, `ChronicleQueueLatencyDistribution.java`, `ThroughputPerfMain.java`.
- Lesson: latency distributions (percentiles), not averages, are the published evidence.

### 2.9 Distributed state

**hazelcast/hazelcast** — https://github.com/hazelcast/hazelcast
- In-memory data grid with SQL, Jet pipelines, CP subsystem (Raft) for locks and atomics; dual-licensed (Apache-2.0 default, Hazelcast Community License for some modules); `hazelcast-archunit-rules` and `hazelcast-spring-boot-autoconfiguration` modules.
- Lesson: distributed locks and leases are a real option for leader election in a Java portfolio, but PostgreSQL advisory locks or ShedLock are simpler at small scale.

**apache/ignite** — https://github.com/apache/ignite
- Distributed database/cache with SQL (Calcite), `benchmarks/`, `ducktests` (Ducktape-based distributed tests), `compatibility` and `wire-compatibility-check` modules.
- Lesson: backward-compatibility test suites are part of a serious data system.

### 2.10 Rate limiting and resilience

**resilience4j/resilience4j** — https://github.com/resilience4j/resilience4j
- Modules: `circuitbreaker`, `ratelimiter`, `bulkhead`, `retry`, `timelimiter`, `hedge`, `cache`, `micrometer`, `spring-boot3`, `spring-boot4`, `reactor`, `feign`. Last push 2026-10-07.
- Lesson: the Spring Boot default for client-side resilience; the differentiator is configuring it per dependency with tested fallbacks and exposed metrics, not merely adding the annotation.

**bucket4j/bucket4j** — https://github.com/bucket4j/bucket4j
- Token-bucket rate limiter with local and distributed backends: `bucket4j-postgresql`, `-redis`, `-hazelcast-all`, `-ignite`, `-caffeine`, `-jcache`; `bucket4j-benchmarks`, `lincheck-tests` (model-checked concurrency), `backward-compatibility-tests`.
- Lesson: distributed rate limiting in PostgreSQL is feasible (row lock per bucket); lincheck is an underused tool for proving concurrent data structures.

### 2.11 Polish-market relevant engineering

**allegro/hermes** — https://github.com/allegro/hermes
- Allegro's message broker on top of Kafka: `hermes-frontend` (REST publishing, returns `Hermes-Message-Id`, 201 only after Kafka ack), `hermes-consumers` (push delivery to HTTP/JMS/Google Pub/Sub subscribers), `hermes-management`, `hermes-console`, `hermes-tracker`, `hermes-schema` (Avro), `hermes-mock`, `hermes-client`.
- Engineering depth: adaptive rate limiting per subscription (`consumers/consumer/rate/ConsumerRateLimiter.java`, `MaxRateSupervisor.java`, `InflightsPool.java`, `AdjustableSemaphore.java`), exponential idle backoff (`idletime/ExponentiallyGrowingIdleTimeCalculator.java`), workload balancing across consumer nodes (`supervisor/workload/WorkloadSupervisor.java`), remote-datacenter fallback with speculative send (default 250 ms), publish timeout 500 ms, leader vs all acks documented with their loss semantics.
- Performance evidence: `hermes-benchmark` (JMH `HermesServerBenchmark`, `HermesConsumerBenchmark`) run by `.github/workflows/benchmarks.yml` with a comparison script (`benchmark-report-compare.py`).
- Testing: Spock unit tests across modules; `integration-tests` with `BatchRetryPolicyTest`, `KafkaRetransmissionServiceTest`, `FilteringAvroTest`, `ConsumingHttp2Test`, `GooglePubSubConsumingTest`; CodeQL and google-java-format workflows.
- Documentation: readthedocs with architecture, publishing semantics, deployment; license Apache-2.0 per README (GitHub shows NOASSERTION).
- Weaknesses: ZooKeeper as metadata store; 872 stars understate industrial maturity; issues tab disabled in favour of discussions.
- Lesson: the most relevant Polish reference for a Kafka-centric Java backend; retransmission, per-subscriber backpressure and benchmark-in-CI are features worth imitating at small scale.

**Allegro tech blog and other Allegro repositories** — https://blog.allegro.tech/
- Verified posts relevant to this portfolio: "Transactions Aren't Enough: The Need For End-To-End Thinking" (2025-12-02, https://allegro.tech/2025/12/transactions-arent-enough.html), "From complexity to simplicity: How to write integrations with external services" (2025-11-06), "How to stub external services in integration tests" (2026-02), "Two schools of TDD explained" (2026-05), "The Seven Deadly Sins of Test Automation" (2025-12-22).
- `allegro/envoy-control` (Kotlin control plane for Envoy, 111 stars) was archived 2026-03-30; `allegro/bigcache` (Go, 8,173 stars) remains active — evidence that Allegro open-sources operational infrastructure, not demos.
- Lesson: Polish reviewers are likely familiar with these posts; aligning terminology (end-to-end consistency, external integration boundaries, stubbing strategy) is low-cost credibility.

### 2.12 Testing, contract and load tooling (cluster)

- **testcontainers/testcontainers-java** (8,752 stars, MIT, push 2026-10-20) — the default for PostgreSQL/Kafka integration tests; used by eventuate examples, transaction-outbox and the sivaprasadreddy course.
- **Shopify/toxiproxy** (12,451, MIT, Go) — TCP fault injection (latency, bandwidth, connection reset); a Testcontainers module exists. Needed for failure tests that are more than "mock throws exception".
- **grafana/k6** (32,016, AGPL-3.0, Go) and **gatling/gatling** (6,954, Apache-2.0, Scala/Java DSL) — load-test scripts should live in the repo with thresholds, as `microservices-demo` keeps its Locust file.
- **pact-foundation/pact-jvm** (1,148, Apache-2.0, Kotlin) — consumer-driven contracts including message pacts; **spring-cloud-contract** has moved to `spring-attic` (archived), which matters when choosing a contract tool in 2026.
- **microcks/microcks** (2,069, Apache-2.0, Java) — mocks and contract tests from OpenAPI/AsyncAPI; **springwolf/springwolf-core** (352, Apache-2.0) generates AsyncAPI docs from Spring Kafka listeners.
- Also surveyed for context: `apache/kafka` (34,110), `spring-projects/spring-kafka` (2,513), `open-telemetry/opentelemetry-java-instrumentation` (2,655), `confluentinc/schema-registry` (2,471, Confluent Community License) vs `Apicurio/apicurio-registry` (946, Apache-2.0), `Netflix/Hystrix` (24,481, maintenance mode since 2018 — a reminder that star counts outlive relevance).

## 3. Cross-cutting observations

1. **Outbox is the dividing line.** Repos that store the event in the business transaction (`library`, `spring-modulith`, `transaction-outbox`, `eventuate-tram`, `debezium-examples/outbox`, `sivaprasadreddy` course) read as engineering; repos that publish after save (`ewolff/microservice-kafka`, most Spring Cloud demos) read as tutorials.
2. **Idempotent consumers are rare in samples.** Only `debezium-examples/outbox` (`MessageLog`), `eventuate-tram` (`received_messages`) and `kafka-streams-examples` (`EventDeduplicationLambdaIntegrationTest`) implement and test them.
3. **Test tiers matter more than test count.** `eventuate-tram-sagas-examples` and `ftgo` separate unit, integration, component, contract and end-to-end tiers in the build; `petclinic-microservices` has chaos scripts but no assertions around them.
4. **Performance evidence lives in the repo.** `hermes`, `disruptor`, `aeron`, `Chronicle-Queue`, `bucket4j`, `debezium` keep benchmarks as source sets and (in hermes) run them in CI with comparison reports.
5. **Archive risk is real.** `Netflix/conductor`, `Netflix/dispatch`, `grafana/oncall`, `camunda-bpm-platform`, `spring-cloud-contract`, `allegro/envoy-control` were all archived within the last three years; dependency choices should be documented with an exit strategy.
6. **Architecture tests are cheap credibility.** `library`, `buckpal`, `hazelcast-archunit-rules` show ArchUnit in three different scales.
7. **Documentation quality correlates with explicit limits.** The best READMEs (`kgrzybek`, `library`, hermes docs) state what is *not* guaranteed (leader-ack loss window, at-least-once replays) rather than only features.
