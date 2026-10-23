# Engineering Skills Matrix for a Flagship Java Backend Portfolio

Derived from the 37 verified mid/senior Java backend postings documented in `job-market-analysis.md` (26 active + 11 recently expired, all read on **2026-10-23**). Junior postings are excluded from the counts. Percentages are rounded to whole numbers.

## 1. How to read the counts

- **Mentioned** = the technology or practice appears anywhere in the posting (required, nice-to-have, or named stack).
- **Required** = listed under requirements/must-have, or named as a core element of the stack the engineer will work in daily.
- N = 37 for every row. Five Warsaw postings describe the same insurance client through different vendors (Avenga reference, ASTEK, IT Factory, TT PSC, SCALO); they inflate Kafka, Hibernate, DDD, Angular and "AI tools" by up to 5 counts. Adjusted figures are noted where this matters.
- Counts are conservative: when a posting said "automated tests" without naming a tool, it counts for "testing expected" but not for JUnit/Testcontainers.

## 2. Normalised technology frequency table

| Rank | Technology / practice | Mentioned (of 37) | Required (of 37) | Notes |
|---|---|---|---|---|
| 1 | Java (named) | 36 (97%) | 33 (89%) | Stripe is language-agnostic; Google, Box, Shaped Thoughts, Allegro accept Java or Kotlin/Scala. Named versions: 17+ (ING, SocGen, Accenture, Netflix, Revolut), 21+ (Nordea, Rightmove, Experis "21/25") |
| 2 | Spring Framework / Spring Boot | 28 (76%) | 27 (73%) | Absent only in platform-internals roles (Netflix as N, Elastic, Box), language-agnostic roles (Stripe, Google) and Revolut (no Spring) |
| 3 | SQL / relational databases | 26 (70%) | 25 (68%) | "Query optimisation", "window functions" appear in ING and EPAM |
| 4 | Messaging of any kind (Kafka, RabbitMQ, JMS/MQ, MQTT, event-driven) | 25 (68%) | 21 (57%) | Event-driven wording accompanies most banking/insurance roles |
| 5 | Cloud (AWS, GCP or Azure) | 22 (59%) | 10 (27%) | Usually "any of AWS/GCP/Azure"; GCP leads among product companies (Allegro, Revolut, Experis, Google, Box via Vertex), AWS among consultancies |
| 6 | REST API design and integration | 21 (57%) | 19 (51%) | OpenAPI explicitly in 3, AsyncAPI in 2 (Nordea, Jit/Grid Dynamics) |
| 7 | Automated testing expected (any) | 20 (54%) | 18 (49%) | Named tools are rare; see rows 27-33 |
| 8 | Microservices | 19 (51%) | 16 (43%) | "Microservices and monoliths" wording in the insurance cluster; Luxoft and Allegro list as N |
| 9 | Kubernetes | 18 (49%) | 10 (27%) | Required at Avenga core banking, Experis, Prime Force, Devire, Luxoft; N in insurance cluster, ING, TSS, SocGen |
| 10 | Apache Kafka | 17 (46%) | 16 (43%) | Adjusted for the insurance cluster: 12/32 (38%). Kafka Streams named by Experis; error handling/retries/scalability named by ING |
| 11 | CI/CD pipelines | 17 (46%) | 13 (35%) | Tools: Jenkins 5, GitHub Actions 3, GitLab CI 2, TeamCity/Harness 1, Argo CD 1 |
| 12 | Docker / containerisation | 17 (46%) | 11 (30%) | |
| 13 | PostgreSQL (named) | 13 (35%) | 11 (30%) | Oracle 5 (banks, Luxoft, TSS), MongoDB 2, Couchbase 1, Snowflake 1 |
| 14 | Hibernate / JPA | 12 (32%) | 12 (32%) | Adjusted: 7/32 (22%). jOOQ named by Revolut and TSS (N) |
| 15 | AI-assisted / agentic development | 12 (32%) | 9 (24%) | Required by Avenga, ASTEK, IT Factory, TT PSC, SCALO, Prime Force (agents, MCP servers), Devire (Copilot/Devin/Claude), Elastic, Pekao; N at Experis, Shaped Thoughts, Allegro |
| 16 | Security (any mention) | 12 (32%) | 6 (16%) | Concrete standards named in only 4: TSS (OAuth2/SAML2/OIDC), Elastic (OAuth2/SAML/TLS/PKI), Experis (JWT/mTLS N), Prime Force (OWASP N) |
| 17 | AWS | 11 (30%) | 5 (14%) | |
| 18 | GCP | 11 (30%) | 4 (11%) | |
| 19 | Observability tooling (Prometheus, Grafana, ELK, OpenTelemetry, New Relic) | 9 (24%) | 4 (11%) | Prometheus/Grafana 7, ELK 4, OpenTelemetry 1 (ING N), New Relic 2 (Revolut). "Production ownership / on-call" adds Box, Netflix, Google, Allegro |
| 20 | Domain-Driven Design | 9 (24%) | 4 (11%) | Required by TSS and the insurance cluster; practised at Revolut |
| 21 | Performance tuning / diagnosing production issues | 9 (24%) | 8 (22%) | ING, Netflix, Luxoft, Google, Elastic, Revolut, ING Hubs, Prime Force, Allegro |
| 22 | Mentoring / technical leadership | 9 (24%) | 6 (16%) | Avenga core banking, Rightmove, Stripe, Netflix, Revolut Lead, Allegro; N in insurance cluster |
| 23 | Clean Architecture / Clean Code / SOLID / design patterns | 8 (22%) | 8 (22%) | TSS, ING, Shaped Thoughts, Nordea, ING Hubs, BlueSoft, Jit Team, Capgemini |
| 24 | Distributed-systems fundamentals (explicit) | 8 (22%) | 8 (22%) | Experis, Shaped Thoughts, Box, Netflix, Elastic, Google, EPAM, Revolut |
| 25 | Azure | 7 (19%) | 2 (5%) | |
| 26 | RabbitMQ | 6 (16%) | 1 (3%) | Société Générale stack; otherwise N |
| 27 | Spock | 5 (14%) | 5 (14%) | Avenga, ASTEK, Nexio, Revolut x2 |
| 28 | Elasticsearch (as a dependency) | 5 (14%) | 2 (5%) | Experis, Rightmove; N in insurance cluster |
| 29 | Angular (frontend expected from backend dev) | 5 (14%) | 5 (14%) | Entirely the insurance cluster plus Capgemini |
| 30 | Kotlin | 5 (14%) | 2 (5%) | Allegro x2, Shaped Thoughts, Google N, Revolut N |
| 31 | Concurrency / multithreading / JVM internals | 5 (14%) | 5 (14%) | ING Hubs, Netflix, Elastic, Avenga Everest, EPAM |
| 32 | Data engineering adjuncts (Spark, Airflow, BigQuery) | 5 (14%) | 1 (3%) | Allegro Ads required; N at Nordea, ING, EPAM, Allegro Product Page |
| 33 | JUnit (named) | 4 (11%) | 4 (11%) | TestNG 2, Mockito 1 |
| 34 | TDD (named) | 4 (11%) | 2 (5%) | Rightmove, Revolut x2; N at Shaped Thoughts |
| 35 | Redis | 4 (11%) | 4 (11%) | ING, Luxoft, Revolut x2 |
| 36 | Schema migrations (Flyway, "DB migrations") | 4 (11%) | 3 (8%) | SocGen, Revolut x2, Allegro Ads |
| 37 | BPMN / process engines (Flowable, Camunda) | 4 (11%) | 1 (3%) | ASTEK requires Flowable; Link Group and Nexio N; Avenga "process improvement" as responsibility |
| 38 | OpenAPI / AsyncAPI (explicit) | 3 (8%) | 3 (8%) | Nordea, TSS, Jit Team (Aviva too, unverified) |
| 39 | Playwright | 3 (8%) | 0 | N only, insurance cluster |
| 40 | SonarQube / static analysis | 3 (8%) | 3 (8%) | Luxoft, Nexio, Capgemini |
| 41 | Event sourcing / CQRS | 3 (8%) | 2 (5%) | Avenga core banking, Revolut Lead; ING N |
| 42 | On-call / "you build it, you run it" | 3 (8%) | 3 (8%) | Shaped Thoughts, Box, Allegro |
| 43 | Testcontainers | 2 (5%) | 2 (5%) | Avenga reference, ASTEK |
| 44 | WireMock | 2 (5%) | 2 (5%) | Avenga reference, ASTEK |
| 45 | Spring Cloud | 2 (5%) | 2 (5%) | Avenga reference, ASTEK |
| 46 | gRPC | 2 (5%) | 1 (3%) | Netflix; Stripe N (EPAM junior N) |
| 47 | Reactive / WebFlux | 2 (5%) | 1 (3%) | Experis, Allegro Product Page |
| 48 | Terraform / IaC | 2 (5%) | 1 (3%) | Sii, Capgemini |
| 49 | Linux / shell | 3 (8%) | 3 (8%) | TSS, Luxoft, EPAM |
| 50 | Single mentions | 1 each | | Micronaut (Avenga), Hazelcast/Ehcache (Experis), GraphQL (Stripe N), OpenTelemetry (ING N), Keycloak (TSS N), Istio/Helm (Capgemini), Argo CD (Devire), Groovy (Nexio), Snowflake (Nordea N), Couchbase (Rightmove) |

## 3. Classification

### 3.1 Core industry expectations (present in roughly half or more of postings)

| Capability | Evidence | Portfolio implication |
|---|---|---|
| Modern Java (17/21+) with idiomatic OOP and functional style | 97% named; versions named are all 17+ | Java 21 toolchain, records, sealed types, pattern matching, virtual threads where justified; no legacy idioms |
| Spring Boot 3.x ecosystem | 76% | Spring Boot 3 with Spring Data JPA, Spring Kafka, Spring Security, Actuator |
| Relational modelling and SQL on PostgreSQL | 70% SQL, 35% PostgreSQL named | Versioned schema (Flyway/Liquibase), indexes with rationale, at least one non-trivial query, transaction boundaries |
| Messaging and event-driven integration, Kafka first | 68% messaging, 46% Kafka | Producer/consumer with explicit error handling, retries, DLQ, idempotency; this is what ING spells out |
| REST API design with a published contract | 57% | OpenAPI 3 spec committed and validated; versioning and error model documented |
| Automated tests at unit and integration level | 54% expected | Unit + integration tests; Testcontainers for PostgreSQL and Kafka because the reference cluster names it |
| Microservice or well-modularised service design | 51% | One or two services with explicit boundaries, or a modular monolith with documented module contracts; postings say "microservices and monoliths" |
| Containerised, reproducible build and run | 46% Docker, 46% CI/CD | Dockerfile, docker-compose, CI pipeline (GitHub Actions) running build, tests and static analysis |

### 3.2 Common senior-level expectations (roughly 20-50%)

| Capability | Evidence | Portfolio implication |
|---|---|---|
| Kubernetes deployment literacy | 49% mentioned | Manifests or Helm chart with probes, resource limits, config/secrets separation; not a full cluster |
| Cloud-neutral design with one concrete target | 59% cloud, split AWS/GCP/Azure | Externalised config, 12-factor, one deployment path (e.g. a free-tier or local kind/k3d cluster); avoid vendor lock-in in code |
| Observability | 24% tooling, plus production-ownership roles | Structured logs, Micrometer metrics, OpenTelemetry traces, Grafana dashboard or at least Prometheus endpoint; a runbook |
| Security basics applied | 32% mention | OAuth2/OIDC resource-server config, input validation, secrets handling, dependency scanning; TSS and Elastic show the identity-centric direction |
| DDD and clean architecture vocabulary | 24% DDD, 22% clean code/patterns | Bounded contexts, aggregates, ports/adapters or layered modules explained in an ADR |
| Performance and production diagnostics | 24% | Load test (k6/Gatling) with numbers, JFR or async-profiler findings, documented bottleneck and fix |
| Hibernate/JPA mastery | 32% | N+1 avoidance, fetch strategies, optimistic locking, explicit transaction scopes |
| Mentoring / written technical leadership | 24% | ADRs, design memos, contribution guide, code review checklist; Netflix explicitly values "writing decisions down" |
| AI-assisted development fluency | 32% mentioned, 24% required | Documented, auditable use of coding agents (e.g. an `AGENTS.md`/`CLAUDE.md`, MCP configuration, review policy); the work itself must still stand on its own |

### 3.3 Advanced differentiators (under 20%, but decisive in platform/fintech roles)

| Capability | Evidence | Portfolio implication |
|---|---|---|
| Distributed-systems correctness: idempotency, outbox, exactly-once semantics, sagas | Explicit in Box, Experis, Revolut, EPAM, Netflix, Shaped Thoughts | Transactional outbox or inbox pattern with tests proving at-least-once delivery handling |
| Event sourcing / CQRS | Avenga core banking, Revolut, ING (N) | Only if the domain warrants it; a small, well-tested event store beats a large half-finished one |
| Resilience patterns | ASTEK, Experis, ING Hubs | Resilience4j circuit breaker/retry/bulkhead with chaos-style tests |
| AsyncAPI / contract-first async | Nordea, Jit Team | AsyncAPI document for Kafka topics next to the OpenAPI spec |
| Concurrency and JVM internals | Netflix, Elastic, ING Hubs, EPAM | Virtual threads vs platform threads benchmark, GC configuration notes, JFR recording |
| Reactive / WebFlux | Experis, Allegro | Optional; justify choice vs virtual threads |
| Security protocols in depth | TSS, Elastic, Experis | mTLS between services, JWT validation, OWASP checklist with evidence |
| GitOps delivery (Argo CD), IaC (Terraform) | Devire, Sii, Capgemini | A minimal Terraform or Argo CD manifest if a cloud target exists |
| gRPC | Netflix, Stripe | Internal service-to-service call in gRPC with protobuf contract |

### 3.4 Specialised technologies (domain- or employer-specific)

| Technology | Where it appears | Verdict for a general portfolio |
|---|---|---|
| BPMN engines (Flowable, Camunda) | Insurance cluster, Link Group, Nexio | Relevant only if the product concept includes a long-running business process; otherwise mention familiarity, do not build around it |
| Oracle / PL-SQL | Luxoft, TSS, Link Group, Avenga Everest | Bank legacy; PostgreSQL skills transfer, skip |
| Spark, Airflow, BigQuery, Snowflake | Allegro, Nordea, ING, EPAM | Data-engineering adjacency; out of scope for a backend flagship |
| Elasticsearch as a dependency | Experis, Rightmove | Only if search is a product feature |
| Micronaut, Groovy, Scala | Avenga, Nexio, Allegro/Box | Niche alternatives; Java 21 + Spring covers the demand |
| Angular | Insurance cluster, Capgemini | Full-stack bench expectation; a minimal UI is optional, not a differentiator |
| PostGIS, GeoServer, Apereo CAS | TSS | Project-specific |
| Hazelcast, Ehcache, Redis | Experis, ING, Luxoft, Revolut | Redis caching is a reasonable small addition; distributed caches are not |

### 3.5 Tools unlikely to improve a portfolio project

| Tool / practice | Reason |
|---|---|
| Jenkins, TeamCity, Harness, uDeploy, Artifactory | Enterprise CI plumbing; recruiters assume transferability from GitHub Actions |
| SOAP / XML Schema | Legacy integration, appears only as N |
| Playwright, Selenium | UI test tooling listed as N; irrelevant without a UI |
| GWT, Struts, Java EE / JavaBeans | Legacy stacks seen in a few Warsaw listings; signal maintenance work, not design skill |
| Consul, Istio, Helm charts beyond basics, service mesh | Platform-team concerns; a README statement of awareness is enough |
| Multiple cloud providers deployed in parallel | Postings ask for "one of", never all three |
| Large frontend (React/Angular) inside a backend flagship | Dilutes the signal and the review time |
| New Relic, Datadog, Snowflake | Commercial SaaS; Prometheus/Grafana/OpenTelemetry demonstrate the same competency free |

## 4. What matters most to demonstrate in a public portfolio repository

Ranked by the share of postings that would check for it, weighted toward the Warsaw senior cluster and the platform roles:

1. **Correct, idiomatic Java 21 + Spring Boot 3 service with a PostgreSQL schema under migration control** (core for ~90% of postings). Without this, nothing else is evaluated.
2. **Kafka integration done properly**: idempotent consumer, retry and DLQ topics, outbox for publishing, Testcontainers-based integration tests. Explicitly spelled out by ING; implied by all 16 Kafka-required postings.
3. **Contract-first APIs**: OpenAPI (and AsyncAPI for topics) committed, validated in CI, with a documented error model and versioning policy. Nordea and Jit Team ask for both; most others ask for "REST API design".
4. **Test strategy that a reviewer can run in one command**: unit, integration (Testcontainers), contract/stub (WireMock), one load test with recorded results. The Avenga/ASTEK cluster names these tools; Rightmove and Revolut want visible TDD discipline.
5. **Operational readiness**: Dockerfile, compose, Kubernetes manifests with probes and limits, Actuator health, Micrometer metrics, OpenTelemetry tracing, a Grafana dashboard JSON, a runbook. Covers Kubernetes (49%), CI/CD (46%), observability (24%) and "you build it, you run it" roles.
6. **Architecture documentation**: C4 or equivalent diagram, 5-10 ADRs covering boundaries, consistency choices, failure modes, and what was deliberately not built. This is how DDD/clean-architecture (24%) and "written decisions" (Netflix, Rightmove, Stripe) are evidenced without a team.
7. **Security hygiene**: Spring Security resource server with OAuth2/OIDC (Keycloak in compose), validation, secrets via environment, dependency and container scanning in CI. Satisfies the 32% that mention security and the identity-heavy roles.
8. **Performance evidence**: one measured bottleneck and its fix (query plan, N+1, thread pool sizing, virtual threads), with before/after numbers. Appears in 24% of postings as "diagnosing production issues".
9. **Transparent, governed AI-assisted workflow**: a repository policy describing which agents/tools were used, how output was reviewed, and tests that prove behaviour. This turns the newest 24%-required expectation into a differentiator while reassuring the employers (Shaped Thoughts) that test for unaided competence.
10. **Clear commit and release history**: conventional commits, tagged releases, CHANGELOG, green CI badge. Recruiters check this in under a minute.

Items that should be mentioned but not built: BPMN engine, Oracle, Spark/Airflow, Angular UI, multi-cloud, service mesh.
