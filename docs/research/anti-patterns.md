# Anti-Patterns in Portfolio Backends and How to Avoid Them

Verification date: 2026-10-23. Observations about public repositories are based on inspecting their Git trees and selected files on that date; they are offered as neutral observations about widely used samples, not as criticism of their authors (most are tutorials and succeed at being tutorials). Where no repository is cited, the point summarises a general tendency seen across many portfolio projects.

## Part A. Overused portfolio categories

| Category | Why common implementations differentiate poorly | Technical extensions that would change that |
|----------|--------------------------------------------------|---------------------------------------------|
| Todo / notes app | CRUD over one table; no invariants, no concurrency, no integration | Offline-first sync with vector clocks and conflict resolution; CRDT-backed shared lists; optimistic concurrency tested under race |
| Generic e-commerce microservices (product/cart/order/payment) | Reviewers have seen hundreds; services are CRUD facades over separate databases talking synchronously (shape of `piomin/sample-spring-microservices-new`, `GoogleCloudPlatform/microservices-demo` at the app layer) | A real saga with compensation matrix and failure tests; inventory reservation with expiry; price-change-during-checkout handling; idempotent payment with provider idempotency keys |
| Basic banking / money transfer | Two-account transfer with `@Transactional`; ignores double-entry, idempotency, reconciliation (`thombergs/buckpal` is explicit that `AccountLock` is a `NoOpAccountLock`) | Double-entry ledger with event sourcing for one aggregate; end-of-day reconciliation job; replay from events; duplicate-transfer protection with idempotency keys |
| Booking / reservation | Insert-if-free without a lock; no hold expiry; no overbooking strategy | Holds with TTL and `FOR UPDATE SKIP LOCKED` dispatch; waitlists; exclusion constraints on time ranges (`tstzrange` + `EXCLUDE USING gist`); deliberate overbooking with a measured no-show model |
| Inventory | Stock count column decremented in place | Reservation ledger, reorder workflows, reconciliation against a simulated warehouse feed with out-of-order events |
| Chat | WebSocket echo with in-memory rooms | Persisted ordering per conversation, read receipts as idempotent events, backpressure for slow clients, message retention and search projection |
| Petclinic clones | The reference is excellent (`spring-petclinic/spring-petclinic-microservices`) and yours will be compared with it | Only valuable if it adds what the reference lacks: outbox between services, contract tests, chaos tests tied to assertions, published load results |
| URL shortener | Hash + redirect; done in an afternoon | Hot-key caching with measured hit ratio, analytics via Kafka Streams windowed counts, abuse rate limiting (`bucket4j-postgresql`), expiry/tombstones and a documented benchmark |
| Thin LLM wrapper | A controller forwarding to a model API; no domain, no state | Treat the model as an unreliable external dependency: timeouts, hedged requests, circuit breakers, cost accounting, idempotent tool execution, evaluation harness with golden sets |
| Kafka demo without a domain workflow | Producer and consumer printing messages; no outbox, no dedupe, no rebalance handling (shape of `ewolff/microservice-kafka`) | Outbox + idempotent consumer + poison-pill DLT + consumer-lag SLO + a windowed Streams aggregate, each covered by an integration test |

Common thread: the domains are not the problem; the absence of invariants, concurrency, failure handling and evidence is. A booking or inventory system becomes distinctive when it encodes rules that can be violated and proves they are not.

## Part B. Engineering mistakes in ambitious portfolio systems

Each item lists a symptom a reviewer can detect in minutes, and a remedy.

### B1. Microservices without independent responsibilities
- **Symptom:** every "service" is `Controller → Service → Repository` over one table; a single business operation requires the gateway to call three services and merge DTOs (`spring-petclinic-api-gateway/.../ApiGatewayController.java` composes owners + visits; `piomin` `organization-service` calls department and employee clients).
- **Remedy:** start as a modular monolith (Spring Modulith modules with ArchUnit tests as in `ddd-by-examples/library` `ModularArchitectureTest.java`); split only along a boundary that has its own data, its own failure mode and its own scaling reason; document that decision.

### B2. Chatty distributed calls
- **Symptom:** N+1 HTTP calls in loops over Feign clients; no timeouts configured; a list endpoint fans out per item.
- **Remedy:** aggregate at the data layer or via a read model fed by events (CQRS §2 of the patterns document); set explicit connect/read timeouts and bulkheads per client; show a trace in the README with the hop count.

### B3. No transactional guarantees across the write and the publish
- **Symptom:** `repository.save(order); kafkaTemplate.send(...)` in the same method (observed shape in `ewolff/microservice-kafka` `OrderService`); no outbox table in the schema; no retry or backlog metric.
- **Remedy:** outbox (Spring Modulith event publication registry or own table with `SKIP LOCKED` relay); a test that kills the relay between commit and publish and asserts eventual delivery.

### B4. Missing idempotency
- **Symptom:** consumers with no dedupe table or version check; POST endpoints without an `Idempotency-Key`; retries configured on the producer but not reasoned about on the consumer.
- **Remedy:** `processed_message(consumer_group, message_id)` written in the consumer's transaction (`debezium-examples/outbox` `MessageLog.java`); idempotency key header on commands with stored response replay; an integration test that delivers the same message twice.

### B5. No failure testing
- **Symptom:** tests only cover the happy path with Mockito; Testcontainers present but used only for schema; chaos tooling present but not asserted (`spring-petclinic-microservices/scripts/chaos/` has attack definitions, no test consumes them).
- **Remedy:** Toxiproxy latency and connection-reset tests around the database and Kafka; kill-and-restart tests for the relay and consumer; a "failure modes" table in the README mapping each mode to the test that covers it, in the spirit of `confluentinc/parallel-consumer` `RebalanceTest`, `DrainCloseTest`, `TransactionTimeoutsTest`.

### B6. Overcomplicated infrastructure
- **Symptom:** Eureka, Config Server, Zuul/Gateway, Admin, Zipkin, Kafka, Redis, Mongo and Postgres in one Compose file for three CRUD services (the `piggymetrics` and early Spring Cloud shape); Kubernetes manifests for an app no one deploys to Kubernetes.
- **Remedy:** every infrastructure component must justify itself with a requirement and a test; prefer PostgreSQL for queues, locks, outbox and rate-limit buckets until measured otherwise (`bucket4j-postgresql`, advisory locks, `SKIP LOCKED`); one `docker compose up` must start the full system in under two minutes.

### B7. Misused design patterns
- **Symptom:** interfaces with a single implementation and an `Impl` suffix everywhere (`AccountService`/`AccountServiceImpl` in `piggymetrics`), factories for value objects, CQRS with identical read and write tables, event sourcing for reference data, "hexagonal" packages whose adapters import domain JPA entities.
- **Remedy:** patterns must be traceable to a force (testability, a second implementation, scaling); enforce with ArchUnit (`thombergs/buckpal` `DependencyRuleTests.java`); explain each pattern choice in an ADR (architecture decision record) of five to ten lines.

### B8. Gateway-only security
- **Symptom:** JWT validated at the gateway, internal services accept any request; `permitAll()` inside services; no method-level authorisation; secrets committed in `application.yml`.
- **Remedy:** resource-server configuration in every service (`piggymetrics` did configure `ResourceServerConfig` per service, which is the right instinct), propagate the token, authorise by tenant/ownership at the domain level, and test a forged or missing token at the service boundary; load secrets from environment variables and document rotation.

### B9. Unvalidated input
- **Symptom:** request DTOs without Bean Validation annotations; domain constructors accepting any values; error responses leaking stack traces; no limits on page size or payload size.
- **Remedy:** `@Valid` at the boundary plus value objects that cannot be constructed invalid (`library` `ISBN`, `PatronId`; `buckpal` `PositiveMoneyValidator`); RFC 9457 problem details; property-based tests (jqwik) for parsers and money arithmetic.

### B10. Unclear domain boundaries
- **Symptom:** shared `common` module with entities used by every service; `Employee` class duplicated in `department-service` and `organization-service` (`piomin`), or a single `model` package holding all tables; events named after tables (`OrderUpdated`) instead of facts (`OrderPaid`).
- **Remedy:** a context map and a glossary in `docs/`; events named as past-tense business facts with explicit schemas; a module per bounded context, enforced by ArchUnit or Spring Modulith's `ApplicationModules.verify()`.

### B11. Unmeasured performance claims
- **Symptom:** README says "high-performance" or "handles millions of events" with no script, environment, or percentiles; no `load/` or `jmh/` directory.
- **Remedy:** commit k6/Gatling scenarios and JMH benchmarks; publish p50/p95/p99 with hardware, dataset and duration; compare in CI like `allegro/hermes` `.github/workflows/benchmarks-comparison.yml`; state what was *not* measured.

### B12. Empty dashboards
- **Symptom:** Prometheus and Grafana in Compose, default JVM dashboard only, no SLO panels; no alert rules; metrics not mentioned in the README.
- **Remedy:** commit a provisioned dashboard JSON (`spring-petclinic-microservices/docker/grafana/dashboards/grafana-petclinic-dashboard.json` is the pattern) with domain panels: outbox backlog and age, consumer lag per group, saga completion and compensation rates, breaker state, p95 per endpoint; add two or three Prometheus alert rules and a runbook section.

### B13. Diagrams that disagree with code
- **Symptom:** README shows Kafka between services that call each other over HTTP; a "CQRS" box with no read model in the code; a service in the diagram that does not exist in `docker-compose.yml`.
- **Remedy:** generate what can be generated (Spring Modulith `Documenter` for module diagrams, AsyncAPI from `springwolf`, OpenAPI from `springdoc`); keep hand-drawn C4 diagrams minimal and dated; add a CI check that every service in the diagram list is present in Compose; review diagrams in the same pull request as the code change.

### B14. Stale or unlicensed dependencies (additional)
- **Symptom:** Netflix OSS stack (Zuul, Hystrix, Ribbon) in a 2026 repository; archived libraries (`spring-cloud-contract` in `spring-attic`, `Netflix/conductor` archived 2023-12, `grafana/oncall` archived); no `LICENSE` file (observed in `piomin/sample-spring-microservices-new`, `thombergs/buckpal`, `ddd-by-examples/all-things-cqrs` as reported by the GitHub API).
- **Remedy:** choose actively maintained successors (`conductor-oss`, Pact JVM, Resilience4j), pin versions with Dependabot/Renovate, add a license, and list third-party licenses in the README.

## Part C. A short review checklist derived from the above

1. Can a reviewer find the outbox table, the relay and the dedupe table in under a minute?
2. Is there one test that delivers a duplicate message and one that kills a component mid-flow?
3. Does each infrastructure container in Compose map to a requirement and a test?
4. Are there committed load scripts and published percentiles with environment details?
5. Does the dashboard JSON contain at least one domain-specific SLO panel?
6. Do ArchUnit or Spring Modulith tests fail when a module boundary is crossed?
7. Does the README state what is not guaranteed (ordering scope, delivery semantics, consistency lag)?
8. Do the diagrams match `docker-compose.yml` and the package structure at the current commit?
