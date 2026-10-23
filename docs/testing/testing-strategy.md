# Testing Strategy

*Phase 7 deliverable. The suite is split by what each layer can prove. Counts and results in the
final section are taken from actual runs (see `docs/engineering-progress.md` for the log).*

## Layers

| Layer | Tool | Runs with | What it proves |
|---|---|---|---|
| Unit | JUnit 5, AssertJ | `mvn test` (surefire, `*Test`) | Rating arithmetic, money rounding, UUIDv7 ordering, state machine, value grammar, token bucket, HMAC signing, AES-GCM round trip |
| Property-based | jqwik | `mvn test` | Invariants of the rating engine over random tier tables and quantities: non-negativity, monotonicity, band-sum equivalence, volume ≤ graduated for decreasing prices, included units never increase the bill, determinism, package ceiling |
| Architecture | Spring Modulith `verify()`, ArchUnit | `mvn test` | No module cycles or `internal` access; controllers never touch repositories; the rating engine depends on no Spring/JPA/clock; entities are not public; Kafka listeners only in worker role |
| Integration | Spring Boot test, Testcontainers (PostgreSQL 17, Kafka 4.1), WireMock, Awaitility | `mvn verify` (failsafe, `*IT`) | Real pipeline behaviour: HTTP → Kafka → consumer → PostgreSQL; locking; redelivery; late events; webhooks; outbox; security; metrics; contracts |
| Contract | networknt JSON Schema, springdoc snapshot | `mvn verify` (`ContractIT`) | Kafka message contract validates/rejects samples; OpenAPI paths and schemas match `docs/api/openapi.json` |
| End-to-end / chaos | Simulator (`tallyard-simulator`), Docker Compose | manual / demo | Thousands of events with duplicates, late and invalid ones while killing the worker; final truth check compares expected vs stored totals |
| Load | k6 | manual | Throughput and latency thresholds for ingest and entitlement checks |

## Design principles

- **Real infrastructure in integration tests.** Deduplication, `FOR SHARE`/`FOR UPDATE` protocols,
  `SKIP LOCKED`, `ON CONFLICT … RETURNING` and Kafka redelivery cannot be meaningfully tested against
  H2 or mocks. Containers are singletons per JVM (`Containers`), so the whole IT suite pays startup
  once.
- **Isolation by tenant, not by truncation.** Every IT creates its own tenant (`TestTenant`), so
  tests run in parallel-safe fashion against one database and never clean up each other's data.
- **Failure injection at the seam that matters.** `MeteringHooks.afterCommit` lets a test throw
  between the database commit and the Kafka acknowledgement — the exact window where at-least-once
  delivery would double count a naive implementation.
- **Assertions on outcomes, not on calls.** No `verify(mock).method()` style; tests read back through
  the public API the way a customer would.
- **Awaitility for eventual consistency**, with generous upper bounds and tight assertions, plus
  "stays that way" checks (`during(...)`) where double counting would show up late.
- **Property tests for arithmetic.** Examples document; properties protect.

## Coverage of acceptance criteria

| Criteria | Tests |
|---|---|
| A1, A2, A4, A6, A7, A8 | `MeteringPipelineIT` |
| A3 | `ConsumerRedeliveryIT` |
| A5, B1, B2, B3, B6, B7 | `BillingLifecycleIT` |
| B4, B5 | `RatingEngineTest`, `RatingEnginePropertyTest` |
| B8, E1, E2 | `OutboxPublicationIT` |
| C1, C2 | `CreditLedgerConcurrencyIT` |
| C3, C4 | `EntitlementIT` |
| D1, D2, D5 + DLT | `IngestApiIT` |
| D3 + isolation + revocation | `SecurityIT` |
| D6, D7 | `ContractIT` |
| E3, E4 | `WebhookDeliveryIT`, `WebhookSignerTest` |
| F2, F3 | `SecurityIT.actuator…`, `MetricsIT` |
| G1, G2 | `ModularityTest`, `ArchitectureTest` |
| C5, F1, F4, F5 | manual: k6, Compose, Jaeger, Grafana (see `docs/performance`, `docs/operations`) |

## What is deliberately not tested automatically

- Kafka broker outage mid-run (needs container stop/start inside the suite; done as a manual drill
  in `docs/testing/failure-scenarios.md`).
- PostgreSQL outage mid-batch (same; the Toxiproxy module is on the classpath for a future test).
- Multi-worker rebalance (covered conceptually by redelivery; demonstrated manually with
  `--scale worker=2`).
- Grafana panel contents (visual; screenshots instead).

## How to run

```bash
mvn test                 # unit + property + architecture (~20 s)
mvn verify               # + integration tests with Testcontainers (needs Docker)
mvn verify -Dit.test=BillingLifecycleIT   # one IT class
mvn verify -Dcontracts.update=true        # refresh docs/api/openapi.json
```

## Results

See the "Verification" section of `docs/engineering-progress.md` for the exact command, date,
counts and durations of the last full run.
