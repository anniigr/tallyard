# Recruitment Evaluation Criteria for a Flagship Java Backend Repository

Purpose: define what a public Java 21 / Spring Boot / PostgreSQL / Kafka portfolio repository must demonstrate to pass review by four kinds of readers encountered in a Middle+/Senior hiring process. Every criterion is tied to evidence that can be checked by opening the repository, and to the postings in `job-market-analysis.md` (all verified **2026-10-23**) that justify it.

The product concept is not fixed; criteria are written to be concept-independent.

## 1. Who reads the repository and for how long

| Reader | Typical time spent | What they can judge | Postings that describe this reader's expectations |
|---|---|---|---|
| Technical recruiter / sourcer | 1-3 minutes | Keyword match, recency, polish, red flags | Every justjoin.it posting with a tag list (Avenga, ASTEK, Experis, SCALO, TT PSC, Prime Force) |
| Senior Java engineer (interviewer) | 15-45 minutes | Code quality, tests, correctness of Kafka/JPA/Spring usage | ING, Nordea, Rightmove, Experis, Avenga core banking, Luxoft |
| Staff/Principal engineer (design round) | 30-60 minutes | Architecture decisions, trade-offs, failure modes, operability | Netflix Java Platform, Box Data Foundations, Revolut Lead, Shaped Thoughts, Stripe, Elastic |
| Engineering manager (hiring manager) | 10-20 minutes | Delivery habits, ownership, communication, risk | Rightmove, Allegro, Box, Shaped Thoughts, ING, Société Générale |

## 2. Technical recruiter perspective

Recruiters screen against the tag list of the vacancy. On justjoin.it the Avenga reference vacancy is tagged Java, Spring Cloud, Spring, Spring Boot, Kafka, SQL, Unit Testing, OOP, Functional Programming (all "advanced"). Recruiters will look for those words and for signs the repository is alive.

| # | Criterion | Evidence that satisfies it | Source postings |
|---|---|---|---|
| R1 | Stack keywords visible in the first screen of the README | A "Stack" section naming Java 21, Spring Boot 3.x, Spring Data JPA/Hibernate, Spring Kafka, PostgreSQL, Flyway, Docker, Kubernetes, GitHub Actions, Testcontainers, WireMock, OpenAPI, Prometheus/Grafana/OpenTelemetry | Avenga reference, ASTEK, Experis, TT PSC, SCALO (tag lists) |
| R2 | Seniority signals in plain language | README states what the author owned end to end: design, implementation, tests, deployment, observability, documentation | ING ("full lifecycle"), Nordea, Box ("own and evolve"), Allegro ("you build it, you run it") |
| R3 | Recency and activity | Last commit within weeks; CI badge green; tagged release; CHANGELOG | All active postings expect current skills; recruiters discard stale repos |
| R4 | One-line value statement and a 60-second demo path | `docker compose up` or `make run` plus a curl/HTTPie example and a screenshot of Grafana or Swagger UI | Prime Force ("prototypes, validate technologies"); Allegro SE1 and Netcompany junior postings show what the baseline looks like, so the flagship must visibly exceed it |
| R5 | Language and location fit | English README (every posting requires B2+ English); Polish summary optional; no personal data beyond name/GitHub | TT PSC, ING, TSS, BlueSoft require Polish C1; Shaped Thoughts C1 both |
| R6 | AI-era compliance signal | A short "How AI tools were used" section with review policy; no claims of hand-writing everything | Avenga, ASTEK, IT Factory, SCALO, Prime Force, Devire, Pekao require AI tool use; Visa "highly desirable" |
| R7 | No red flags | No secrets in history, no `.idea`/`target` committed, no commented-out blocks, no TODO-littered main branch, licence present | General hygiene; SonarQube mentioned by Luxoft, Nexio, Capgemini |
| R8 | Keyword honesty | Only technologies actually used are listed; "familiar with" items separated from "used here" | Shaped Thoughts and Revolut interview with live coding and architecture tasks; inflated READMEs fail later |

Recruiter pass condition: criteria R1-R4 and R7 met. R5, R6, R8 differentiate between candidates with equal stacks.

## 3. Senior Java engineer perspective

This reader opens `src/`, the tests and the build file. They will compare what they see against how they would do it at ING, Nordea or Experis. They look for correctness and for whether they would approve the pull request.

| # | Criterion | Evidence that satisfies it | Source postings |
|---|---|---|---|
| S1 | Modern Java used deliberately | Records for DTOs/value objects, sealed interfaces for domain states, switch pattern matching, `Optional` only at boundaries, no raw types, virtual threads only where I/O-bound and measured | Nordea (Java 21+), Experis (21/25), Rightmove (21+), Netflix (17/21) |
| S2 | Spring Boot configured, not cargo-culted | Constructor injection, `@ConfigurationProperties` records, profiles for local/test/prod, Actuator with limited exposure, no field injection, no `@Autowired` on fields | 27 postings require Spring Boot; ING asks for Clean Code/SOLID |
| S3 | Persistence done correctly | Flyway migrations numbered and reversible where feasible; entity mapping with explicit fetch strategy; no N+1 in listed queries (proved by a test using Hibernate statistics or a query-count assertion); optimistic locking where concurrent updates are possible; explicit `@Transactional` boundaries in the service layer | Hibernate/JPA in 12 postings; ING "query optimisation"; EPAM "window functions, optimisation"; SocGen and Revolut use Flyway |
| S4 | Kafka integration that survives failure | Idempotent consumer (dedup key or inbox table); retry topic and dead-letter topic with backoff; transactional outbox for publishing domain events; consumer group and partition key choice explained; serialisation with schema (JSON Schema or Avro) | ING ("asynchronous messaging, error handling, retries, scalability"), Experis (Kafka Streams), Avenga core banking (event sourcing), Box (data consistency) |
| S5 | Test pyramid that runs locally in one command | Unit tests fast and framework-free where possible; `@SpringBootTest` slices used sparingly; Testcontainers for PostgreSQL and Kafka; WireMock for external HTTP; one contract test from the OpenAPI spec; one load test (k6 or Gatling) with committed results | Avenga reference and ASTEK name JUnit/Spock/Testcontainers/WireMock; Rightmove and Revolut demand TDD; Shaped Thoughts demands acceptance and performance tests |
| S6 | Error handling and API contract | `@ControllerAdvice` with RFC 9457 problem details; validation with Bean Validation; OpenAPI generated or hand-written and verified against the running app in CI | REST in 21 postings; OpenAPI at Nordea, TSS, Jit Team |
| S7 | Concurrency awareness | No shared mutable state in singletons; executor sizing explained; idempotency under retries demonstrated by a test | ING Hubs ("concurrency"), EPAM ("multithreading"), Netflix, Elastic |
| S8 | Code organisation reflects the domain | Package-by-feature or hexagonal layout; domain layer free of Spring/JPA annotations or a documented reason why not; a `module-info` or ArchUnit test enforcing dependencies | DDD in 9 postings; TSS "Clean Architecture, DDD"; Shaped Thoughts "clean architecture" |
| S9 | Build and static analysis | Gradle (Kotlin DSL) or Maven with version catalogue/BOM; Spotless/Checkstyle; Error Prone or NullAway; JaCoCo threshold; dependency vulnerability scan (OWASP Dependency-Check or Gradle plugin) | SonarQube at Luxoft, Nexio, Capgemini; Gradle at Rightmove, Netflix |
| S10 | Git history tells a story | Small, well-named commits; branches merged via PRs with descriptions; no "fix", "wip" chains on main | Universal; Rightmove "left-shifting" pipeline culture |

Senior engineer pass condition: S1-S6 fully met; S7-S10 mostly met. A single serious defect in S3 or S4 (N+1, non-idempotent consumer, missing DLQ) usually ends the review, because these are the exact failure modes the banking postings describe.

## 4. Staff / Principal engineer perspective

This reader skips most code and reads `docs/`, the ADRs, the diagrams, the Kubernetes manifests and the runbook. They evaluate judgement: what was built, what was deliberately not built, and whether the author understands how the system fails.

| # | Criterion | Evidence that satisfies it | Source postings |
|---|---|---|---|
| P1 | Problem statement and non-functional requirements written first | `docs/architecture/overview.md` with context, actors, NFRs (latency target, throughput, availability, consistency model, data retention) and explicit out-of-scope list | Avenga reference and ASTEK ("functional and non-functional requirements"); Netflix ("footprint budgets backed by benchmarks") |
| P2 | Architecture decision records | 5-10 ADRs: modular monolith vs microservices; sync vs async boundary; outbox vs CDC; consistency model; schema evolution; authentication approach; observability stack; why Kafka over RabbitMQ; what would change at 10x load | Netflix ("writing decisions down"), Rightmove ("lead technical decisions"), Stripe ("scope, design"), Revolut Lead ("set priorities") |
| P3 | Diagrams at two levels | C4 context and container diagrams; one sequence diagram for the main flow including failure path; one for the retry/DLQ path | Société Générale ("architecture documents"), ING ("technical documentation", UML), TSS (UML, OpenAPI) |
| P4 | Failure-mode analysis | Table of failure scenarios (broker down, DB failover, duplicate message, poison message, slow downstream, clock skew) with detection, mitigation and test reference | ING Hubs ("resilience in production"), ASTEK ("resilience and reliability mechanisms for distributed systems"), Box ("data consistency, synchronisation, reliability") |
| P5 | Consistency and idempotency reasoning | Explicit statement of delivery guarantees (at-least-once + idempotent handlers), transactional boundaries, and a test that injects duplicates and reorders | Box, Experis, Revolut ("CQRS and event-driven"), Avenga core banking ("event sourcing") |
| P6 | Operability | Kubernetes manifests with readiness/liveness/startup probes, resource requests and limits, PodDisruptionBudget, graceful shutdown handling in Spring; Grafana dashboard JSON; alert rules; `RUNBOOK.md` with "what to do when" | Kubernetes in 18 postings; Devire (Argo CD, Prometheus, Grafana, Elastic); Experis (Prometheus, Grafana); Box and Allegro on-call |
| P7 | Observability as design, not add-on | Correlation IDs propagated through HTTP and Kafka headers; OpenTelemetry traces spanning producer and consumer; business metrics (not only JVM metrics); log levels and structured JSON | ING (Prometheus/Grafana/OpenTelemetry/ELK), Netflix ("metrics, configuration, observability primitives"), Nexio (ELK/Grafana) |
| P8 | Security architecture | Threat model one-pager; OAuth2 resource server with Keycloak in compose; secrets externalised; TLS between services documented (or explicitly deferred with reason); dependency scanning in CI; SBOM generated | TSS (OAuth2/SAML2/OIDC), Elastic (OAuth2/SAML/TLS/PKI), Experis (JWT/mTLS), Prime Force (OWASP), Avenga core banking ("highly secure environment") |
| P9 | Evidence-based performance work | A `docs/performance.md` with method, environment, baseline numbers, one identified bottleneck, the fix, and the after numbers; JFR or async-profiler artefact referenced | ING ("diagnosing performance issues"), Netflix ("benchmarks and regression checks"), Luxoft ("re-architect for performance"), Allegro Product Page (50k rps, latency) |
| P10 | Evolution and versioning strategy | API versioning policy; Kafka schema compatibility mode; database expand/contract migration example; deprecation note | Netflix ("interface stability, versioning, compatibility"), Nordea (OpenAPI/AsyncAPI contracts) |
| P11 | Scope discipline | A "Not built, and why" section; no half-implemented CQRS, no second cloud, no BPMN engine unless the domain needs it | Luxoft and Allegro list microservices as nice-to-have; Shaped Thoughts and Revolut emphasise shipping |

Staff pass condition: P1, P2, P4, P6 are mandatory; P5 and P7 separate "senior" from "senior+"; P9 and P10 are what a Staff reviewer at Netflix or Box would expect to discuss in the design interview.

## 5. Engineering manager perspective

The manager asks whether this person will deliver predictably in a team, communicate clearly, and reduce risk. They read the README, the PR history, the issue tracker, and the contribution/process docs.

| # | Criterion | Evidence that satisfies it | Source postings |
|---|---|---|---|
| M1 | Clear scope and delivered increments | Milestones or GitHub Projects board; releases tagged with notes; roadmap distinguishing done/next/never | Rightmove ("balance feature work with tech debt"), Allegro ("shape technical direction") |
| M2 | Working software over claims | Everything in the README is reproducible by `make up && make test && make load-test`; CI runs the same commands | Prime Force ("validate technologies"), Shaped Thoughts ("design, implement, review, test, deploy") |
| M3 | Communication quality | README readable by a non-specialist in the first screen; ADRs concise; commit messages explain why | Avenga core banking ("communicate with technical and non-technical audiences"), ING ("requirements workshops") |
| M4 | Ownership mindset | Runbook, on-call notes, post-incident style write-up of a bug found during load testing | Allegro, Box, Shaped Thoughts ("you build it, you run it"), Société Générale (L3 support, weekend releases) |
| M5 | Team-readiness | `CONTRIBUTING.md`, PR template, code-review checklist, definition of done, branch protection described | Rightmove ("coordinating work across multiple engineers"), Avenga core banking ("oversee junior engineers"), insurance cluster lists mentoring as N |
| M6 | Risk awareness | Security scanning and dependency updates (Dependabot/Renovate) enabled; licence compliance noted; data handling (no real PII in fixtures) | Banks and payments (ING, Nordea, SocGen, Experis, Stripe) operate under compliance; Elastic lists FedRAMP/SOC 2 |
| M7 | Responsible AI-tool usage | Policy file describing tools used, review steps, and tests that gate merges; any generated code is covered by tests and reviewed in PRs | Avenga, ASTEK, IT Factory, TT PSC, SCALO, Prime Force, Devire require AI tooling; Shaped Thoughts prohibits AI in interviews, so the manager must see independent competence too |
| M8 | Learning and reflection | A "Lessons learned / what I would do differently" section; honest limitations list | Rightmove ("proactively introduces new ideas"), Netflix ("guide engineering teams toward the right path") |
| M9 | Fit for Warsaw-market delivery model | Demonstrates comfort with B2B-style autonomy: self-managed backlog, estimates in milestones, documentation written without being asked | Most Warsaw postings are B2B with 1 office day; Edge One ("analyse problems independently and propose solutions"), Netcompany junior posting contrasts with "manage your own work" |

Manager pass condition: M1-M4 mandatory; M5-M7 strongly preferred; M8-M9 differentiate.

## 6. Consolidated checklist (repository artefacts)

| Artefact | Satisfies | Priority |
|---|---|---|
| `README.md` with stack, 60-second run, architecture summary, AI-usage note, limitations | R1-R6, M2, M3, M7, M8 | Must |
| `docs/architecture/overview.md`, C4 diagrams, sequence diagrams | P1, P3 | Must |
| `docs/adr/0001-...md` through at least `0005` | P2, P10, P11 | Must |
| `docs/failure-modes.md` | P4, P5 | Must |
| `docs/performance.md` with numbers | P9, S5 | Should |
| `RUNBOOK.md` and Grafana dashboard JSON, alert rules | P6, P7, M4 | Must |
| `openapi.yaml` validated in CI; `asyncapi.yaml` for topics | S6, P10 | Must / Should |
| Flyway migrations with expand/contract example | S3, P10 | Must |
| Outbox + idempotent consumer + retry/DLQ with tests | S4, P5 | Must |
| Testcontainers (PostgreSQL, Kafka), WireMock, ArchUnit, k6/Gatling results | S5, S8 | Must |
| `Dockerfile`, `docker-compose.yml` (app, PostgreSQL, Kafka, Keycloak, Prometheus, Grafana, OTel collector) | R4, P6, P8 | Must |
| `k8s/` manifests or Helm chart with probes and limits | P6 | Should |
| GitHub Actions: build, test, static analysis, dependency scan, image build, SBOM | S9, M6 | Must |
| `CONTRIBUTING.md`, PR template, code-review checklist | M5 | Should |
| `AGENTS.md` / `CLAUDE.md` or equivalent AI-usage policy | R6, M7 | Should |
| Tagged releases and `CHANGELOG.md` | R3, M1 | Should |
| Threat model one-pager, secrets handling, OAuth2 resource server | P8 | Should |

## 7. Gaps between what postings ask and what a solo repository can prove

- **Team behaviours** (mentoring, cross-team coordination, stakeholder management) appear in 24% of postings and cannot be demonstrated directly. Written artefacts (ADRs, review checklist, contribution guide) are the only proxy; the repository should say so.
- **Production scale** (Allegro's 50k rps, Stripe's "billions of money movements", Netflix's footprint budgets) cannot be reproduced. Load tests with honest, small numbers and an explicit "how this would change at scale" ADR are more credible than inflated claims.
- **Domain knowledge** (insurance claims, payments, financial markets) is required in ING Hubs, Experis, Revolut and the insurance cluster. The product concept, once chosen, should pick a domain adjacent to one of these clusters so that the vocabulary in the repository matches the vocabulary in the postings.
- **Polish language** is required by about a third of Warsaw postings; the repository cannot prove it, but a short Polish summary in the README costs nothing.
- **Interview reality**: Shaped Thoughts runs a 1-hour live coding and a 1-hour architecture task without AI assistance; Revolut and Rightmove test TDD. The repository earns the interview; it does not replace it. Every pattern in the repository should be one the author can re-derive on a whiteboard.
