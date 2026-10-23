# Engineering Progress

*Running log of phases, decisions, files, verification and open items. Kept in the repository so the
work can be resumed coherently. Dates are real (2026-10-23).*

## Completed phases

| Phase | Deliverables | Status |
|---|---|---|
| 1 Market research | `docs/research/job-market-analysis.md`, `engineering-skills-matrix.md`, `recruitment-evaluation.md` (37 verified postings; reference vacancy active) | Done |
| 2 Open-source landscape | `docs/research/open-source-landscape.md` (45 repos, live metadata), `architectural-patterns.md`, `anti-patterns.md` | Done |
| 3 Concept selection | `docs/research/15-project-concepts.md` (16 concepts), `top-five-comparison.md`, `final-project-selection.md` → **Tallyard** | Done |
| 4 Product | `docs/product/product-specification.md`, `use-cases.md`, `acceptance-criteria.md` | Done |
| 5 Architecture | `docs/architecture/architecture.md` (9 diagrams), `technology-decisions.md`, `failure-model.md`, `adr/ADR-001…008` | Done |
| 6 Implementation | `tallyard-app` (11 modules), `tallyard-simulator`, Flyway schema, dashboard UI | Done |
| 7 Testing | 41 unit/property/architecture tests; 36 integration tests (Testcontainers) — all green, see Verification | Done |
| 8 Performance | `docs/performance/` | Pending measurement |
| 9 Observability | Metrics, traces, health, Grafana dashboard, `docs/operations/` | Done (screenshots pending) |
| 10 Security | `docs/security/security-review.md` | Done |
| 11 DevOps | `deploy/docker-compose.yml`, `deploy/Dockerfile`, `Makefile`, `.github/workflows/ci.yml` | Done (CI run pending push) |
| 12 GitHub | Repository creation, branches, commits | Pending |
| 13 README | Pending |
| 14 Interview | `docs/interview/` (walkthrough, 48 Q&A, defence, ownership checklist) | Done |
| 15 Review | Pending |

## Important decisions

- Modular monolith, two runtime roles (ADR-001).
- Dedup = unique key + `ON CONFLICT DO NOTHING RETURNING`, ack after commit (ADR-002).
- Period lock protocol `FOR SHARE` vs `FOR UPDATE`; late events → adjustments (ADR-003).
- Pure rating engine + replay as-of rating time (ADR-004).
- Entitlement staleness = cache TTL (ADR-005). Hand-rolled outbox (ADR-006). JDBC on hot path
  (ADR-007). No LLM in core (ADR-008).

## Bugs found and fixed during development (kept for honesty)

| Symptom | Root cause | Fix |
|---|---|---|
| Startup failed: `bpchar` vs `varchar(64)` | `char(n)` columns vs Hibernate validation | `varchar` |
| Seeder failed: `MultipleBagFetchException` | Two `List` collections join-fetched | `Set` + `@OrderBy` |
| Overview 409 | JSONB `?` operator parsed as a bind parameter | `jsonb_exists()` |
| Rating failed: `billing_period_one_open` violated | Hibernate orders inserts before updates | `flush()` before creating the successor |
| Late events rejected `NO_OPEN_PERIOD` after early rating | Open period looked up by time; successor starts in the future | `lockOpenPeriod` by state |
| Consumer retried partition forever | Poison payload (not JSON) inserted into a `jsonb` column failed the whole batch | Wrap raw payload as a JSON string |
| Rate limiter allowed 2 s of burst | GCRA baseline offset by capacity | `base = max(current, now)` |
| Nested `@Valid` list → 500-ish shape | `HandlerMethodValidationException` not handled | Dedicated handler |

## Verification log

| When | Command | Result |
|---|---|---|
| 2026-10-23 11:30 | `mvn test -pl tallyard-app` | 41 tests, 0 failures (unit, property, Modulith, ArchUnit) |
| 2026-10-23 11:40 | `mvn verify -pl tallyard-app` | 36 ITs: 25 passed, 11 failed → root causes above |
| 2026-10-23 11:56 | `mvn verify -pl tallyard-app` after fixes | **41 unit + 36 integration tests, 0 failures, 47.7 s** (Testcontainers PostgreSQL 17 + Kafka 4.1) |

## Open items / next actions

1. Full IT suite green; record counts here.
2. Build the Docker image; run the Compose stack; run simulator + k6; record `docs/performance`.
3. Screenshots/GIF of dashboard, Grafana, Jaeger into `docs/assets`.
4. README.
5. Git history (`main` initial, `develop` work, merge), GitHub repository, push, CI verification.
6. Independent review (Phase 15) and fixes.
