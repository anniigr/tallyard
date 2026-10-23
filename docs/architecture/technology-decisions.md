# Technology Decisions

*Each row states why a technology is in (or out). "Earned its place" means it
solves a concrete problem in Tallyard that would otherwise require hand-written code or leave a gap.*

## Selected

| Technology | Version | Responsibility in Tallyard | Why this and not an alternative |
|---|---|---|---|
| Java | 21 (LTS) | Language; records, sealed switch patterns, virtual threads | Most recent LTS supported by every library in the stack; Java 25 is supported by Spring Boot 4 but was not available on the development machine |
| Spring Boot | 4.1.2 | Application framework, auto-configuration, actuator | Current GA line (Spring Framework 7, Jakarta EE 11, Jackson 3); using the 3.x line would mean building on a branch whose OSS support ended in mid-2026 |
| Spring Modulith | 2.1.2 | Verified module boundaries, module documentation | Keeps the modular monolith honest: `ModularityTest` fails the build on cyclic or internal-package dependencies. Its event publication registry is **not** used: the outbox is hand-rolled so that its guarantees are explicit and interview-defensible |
| Spring Data JPA / Hibernate | 7.4 | Catalog, period state machine, invoices, credits, webhooks | Entities with real invariants and optimistic/pessimistic locking benefit from JPA. Schema is never generated: `ddl-auto=validate` |
| Plain JDBC (`JdbcTemplate`) | – | Hot path: event log insert, bucket upsert, replay queries, outbox/webhook polling | Set-oriented SQL (`INSERT … ON CONFLICT … RETURNING`, batched upserts, `FOR UPDATE SKIP LOCKED`) that JPA cannot express; no dirty-checking overhead on thousands of rows per second |
| PostgreSQL | 17 | System of record: event log, buckets, periods, invoices, ledger, outbox | Unique constraints as the deduplication mechanism, partial unique indexes for state invariants, row-level locks, `jsonb`, `SKIP LOCKED`. One database keeps every write transactional |
| Flyway | 12 | Versioned schema migrations | Single SQL file per version, validated at startup against entity mappings |
| Apache Kafka | 4.1 (KRaft) | Durable, partitioned event log between ingestion and metering; billing event bus | Per-customer ordering via partition keys, replay, backpressure, consumer groups for horizontal scaling. No ZooKeeper |
| Spring Kafka | 4.1 | Batch listener, manual acks, error handler with backoff | Batch mode with `MANUAL_IMMEDIATE` acks is what makes "one transaction per batch, ack after commit" possible |
| Spring Security | 7.1 | Stateless API-key authentication, scope enforcement | A single filter + `@PreAuthorize` is enough; OAuth2 would add an identity provider the product does not need |
| springdoc-openapi | 3.1 | OpenAPI 3.1 document and Swagger UI | Generated from code and snapshotted into `docs/api/openapi.json` by `ContractIT` |
| Micrometer + Prometheus | 1.16 / 1.7 | Metrics (custom counters/timers + Kafka client + Hikari + JVM) | Standard pull-based metrics; Grafana dashboard provisioned from the repository |
| OpenTelemetry (Micrometer Tracing bridge) | 1.62 | Distributed traces HTTP → Kafka → consumer → JDBC, exported over OTLP to Jaeger | Trace context propagates through Kafka headers; one trace shows an event's whole life |
| Resilience4j | 2.4 | Per-endpoint circuit breaker for webhook delivery | Stops hammering a dead endpoint; backoff and dead-lettering are hand-written because they must be persisted |
| Caffeine | 3.2 | In-process caches: API-key lookups, catalog hot-path lookups, entitlement snapshots | Bounded, TTL-based; the TTL is the documented staleness bound |
| Testcontainers | 2.0 | Real PostgreSQL and Kafka in integration tests | Tests that matter (dedup, locking, redelivery) cannot be meaningful against H2 or an embedded broker |
| WireMock | 3.13 | External webhook receivers in tests (5xx, flapping, recovery) | Deterministic failure injection for outbound HTTP |
| jqwik | 1.10 | Property-based tests for the rating engine | Monotonicity and band-sum invariants over random tier tables catch what examples miss |
| ArchUnit | 1.5 | Layering rules Modulith does not express | e.g. controllers never touch repositories; the rating engine has no Spring/JPA/clock dependencies |
| Docker Compose | – | One-command local stack | Reviewers run everything with `make up`; a Kubernetes cluster would be a barrier, not a feature |
| k6 | – | Load profiles with thresholds | Scriptable, CI-friendly; results are reported in `docs/performance` only when actually measured |
| GitHub Actions | – | Formatting, unit, integration (Testcontainers), Docker build, secret scan | Standard; caches Maven |

## Evaluated and rejected

| Technology | Why not |
|---|---|
| Microservices (separate deployables per module) | Every module shares one transactional boundary with the event log and periods. Splitting them would replace local transactions with sagas for no scaling benefit. See [ADR-001](adr/ADR-001-modular-monolith.md) |
| Spring Cloud (Gateway, Config, Discovery) | Solves problems of many-service systems; Tallyard has two roles of one artifact |
| Redis | Considered for entitlement snapshots and dedup. Rejected: dedup must be transactional with the write (PostgreSQL unique key), and an in-process TTL cache gives the same staleness bound with one fewer dependency. Documented as the first thing to add for multi-node read-your-writes |
| Schema Registry / Avro | One producer, one consumer, one schema version. JSON Schema in the repository plus a version header is sufficient and reviewable; Avro would add a registry container for a single topic |
| Kafka Streams / ksqlDB for aggregation | Aggregates must be queryable with the rest of the data in one transaction and reproducible from the log; PostgreSQL buckets do that. Streams state stores would create a second source of truth |
| Temporal / Camunda | The period lifecycle is a four-state machine driven by time; a scheduler with `SKIP LOCKED` and `@Version` covers it. A workflow engine would be the heaviest component in the system |
| Event sourcing for the catalog/periods | The usage log *is* event-sourced (immutable, replayable). Event-sourcing the catalog adds projections for data that is read far more than written |
| CQRS with a separate read store | The dashboard reads come from the same PostgreSQL; a read store would be premature |
| Elasticsearch / OpenSearch | No full-text or analytical search requirement |
| gRPC | External API consumers are SDKs and browsers; REST + OpenAPI is the expected contract. Internal calls are in-process |
| Kubernetes manifests / Helm / Terraform | Would be untested YAML. Compose is run and verified; the Dockerfile is production-shaped (layered, non-root) and portable to any orchestrator |
| LLM / agentic features | No core workflow needs probabilistic output; see [ADR-008](adr/ADR-008-no-ai-in-core-path.md) |
| Bucket4j | A per-node token bucket is 40 lines; the library would add a dependency for the one-node case only |
| Lombok | Records and explicit constructors keep entities readable without annotation processing surprises on Jakarta/Hibernate upgrades |
| ShedLock | Scheduler coordination is done with `FOR UPDATE SKIP LOCKED`, which also distributes the work rather than electing one runner |
