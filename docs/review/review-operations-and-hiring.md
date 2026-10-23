# Independent Review: Operations (SRE) and Hiring-Manager Perspectives

*Review date: 2026-10-23. Scope: repository state on branch `develop` (HEAD `12fb25a`), the running
Compose stack on the reviewer's machine (`api`, `worker`, PostgreSQL, Kafka, Prometheus, Grafana,
Jaeger, all healthy), and the documentation set. No files other than this report were modified. No
builds were run; the author's last full run (41 unit + 36 integration tests passing) is taken as given.
Live checks were read-only: `/actuator/health/*`, `/actuator/prometheus`, Prometheus `/api/v1/query`,
`GET /v1/periods`, `GET /v1/invoices`, `GET /v1/usage/stats`, and `docker logs`.*

Two reviewers, two lenses:

- **Reviewer C — Site Reliability Engineer.** Can I deploy, monitor, recover and explain this system
  at 3 a.m.? Are the operational claims true?
- **Reviewer D — Technical Hiring Manager (Senior Java backend, Warsaw/EU).** Would I shortlist the
  author on this repository alone? Does every claim hold up under a 30-minute read?

---

## 1. Scores

### Reviewer C (SRE): 5.5 / 10

The deployment and observability *scaffolding* is well above the portfolio average: one-command
Compose stack with real healthchecks and ordered `depends_on`, a readiness group that includes a
genuine Kafka contributor (verified live: `kafka.status=UP`, `nodes=1`), Prometheus scraping both
roles (both targets `up`), a provisioned Grafana dashboard whose every PromQL expression resolves to
metrics the code actually emits (verified against `/actuator/prometheus` and Prometheus series), a
consumer error handler with the right shape (unbounded exponential retry for whole batches, per-record
dead-lettering), a transactional outbox with `SKIP LOCKED`, and runbooks whose metric names match the
code.

The score is held down by one fact and several omissions:

1. **The running stack is broken in the way that matters most for a billing system.** The worker log
   contains 102 `Billing scheduler: rate failed` errors (one every 5 s) caused by a foreign-key
   violation on `credit_ledger_entry.invoice_id`; 38 of 60 billing periods are stuck in `CLOSING`
   past their grace deadline and only 2 invoices exist. Rating is dead for the whole demo tenant
   because the batch transaction rolls back every period in the batch when a single prepaid customer
   fails (F-01, F-02). No metric, alert or dashboard panel reveals this; an operator would learn it
   from customers.
2. No `restart:` policy on any Compose service although the failure model relies on one; no container
   memory limits for two JVMs sized at 70 % of the host; graceful-shutdown timeout longer than the
   Compose kill timeout (F-03, F-09, F-10).
3. Several headline performance numbers (peak lag 4,142, batch p50/p99 41/192 ms, 2,000 stored/s on a
   single worker) are not present in `docs/performance/raw/`, despite the claim "raw outputs are in
   `raw/`. Nothing is extrapolated" (F-05).

Fix F-01/F-02 and add the two missing billing signals and this is a 7.5.

### Reviewer D (Hiring Manager): 7 / 10

This is a memorable, original and technically dense repository. The problem (metering, late events,
settlement, replayable invoices) is a real Senior-level problem; the mechanisms (idempotent
multi-row insert-returning, `FOR SHARE`/`FOR UPDATE` lock protocol, pure rating engine with property
tests, hand-rolled outbox, GCRA rate limiter, Spring Modulith + ArchUnit boundaries) are exactly the
things a Senior Java interview probes, and each is backed by a readable test. The documentation is
honest about non-guarantees, AI assistance and laptop-only measurements. The 30-second / 5-minute /
30-minute reading paths all work: README headline table, `MeteringService.process` with a numbered
Javadoc, then ADRs.

Why not higher:

1. The flagship claim ("billing-grade", "produces auditable invoice drafts") is contradicted by the
   live system: prepaid customers cannot be rated, and the test suite was shaped around the bug rather
   than catching it (`CreditLedgerConcurrencyIT` inserts an invoice shell *before* applying credits;
   production code does the reverse) (F-01, F-04). A hiring manager who runs `make up` and waits ten
   minutes sees red in the logs.
2. The tagline says "counts exactly once" while ADR-002 and the README's own trade-off section say
   "Effectively-once, not exactly-once — honest naming". Interview documents repeat "exactly-once
   counting". This is the one phrase every Kafka interviewer will pounce on (F-06).
3. `docs/research/` (job-market analysis, "recruitment evaluation criteria") and `docs/interview/`
   (48 Q&A, "ownership checklist") are shipped inside the product repository. They are good
   documents, but they tell the reader the repository was engineered to pass recruiters, and they
   hand an interviewer the candidate's crib sheet (F-14).
4. Signs of a one-day, AI-assisted build that a careful reader will notice: every document is dated
   2026-10-23, every document opens with "Phase N deliverable", the progress log still says README and
   performance are "Pending" while both exist, and one cited test class does not exist (F-22, F-24).

With F-01/F-04/F-06 fixed and the meta-documents moved out, this is an 8.5 — a genuinely strong
Senior portfolio piece.

---

## 2. Findings

Severity: Critical (defeats a core claim or blocks operation), High (will bite in the first week of
real use), Medium (visible to a careful reviewer; fix before publishing), Low (polish).
Type: **O** = objective defect (reproducible, contradicts code/docs), **S** = subjective opinion.

| ID | Severity | Type | Location | Description | Suggested fix |
|---|---|---|---|---|---|
| F-01 | **Critical** | O | `tallyard-app/src/main/java/io/tallyard/billing/internal/BillingService.java:176-206`; `credits/internal/CreditService.java:102-114`; `db/migration/V1__core_schema.sql:242-248` | `rateLocked` calls `credits.apply(..., invoiceId, ...)` at line 178 and only saves the `InvoiceDraft` at line 206. `CreditService.apply` persists `CreditLedgerEntry(invoiceId)` and calls `grants.flush()` (line 114), so the ledger row is inserted before `invoice_draft` exists and violates `credit_ledger_entry_invoice_id_fkey`. **Live evidence:** 102 occurrences of `ERROR: insert or update on table "credit_ledger_entry" violates foreign key constraint` in `docker logs tallyard-worker-1`; `GET /v1/periods` shows 38 × `CLOSING` with `graceUntil` in the past, 20 × `OPEN`, 2 × `RATED`; `GET /v1/invoices` returns 2. Any customer with a credit grant can never be rated. Via `POST /v1/periods/{id}/rate` the operator receives a misleading 409 `integrity_violation` ("conflicts with existing data"). | Persist and flush the invoice before applying credits (`invoices.saveAndFlush(invoice)` before `credits.apply`, then update `creditsApplied`/`total`), or declare the FK `DEFERRABLE INITIALLY DEFERRED`. Add `BillingLifecycleIT.prepaidCustomerIsRatedWithCreditsApplied` that rates a customer with a grant through the public API and asserts `creditsApplied > 0`. |
| F-02 | **High** | O | `BillingService.java:296-304` (`rateDue`), `:288-294`, `:306-312`; `BillingScheduler.java:28-33` | `rateDue` is one `@Transactional` method that rates up to 25 periods; a single failing period rolls back all 25 and the same batch is retried every 5 s forever. This is why *every* customer in the demo tenant is stuck, not just the prepaid ones. The same shape applies to `closeDue`/`finalizeDue`. The Javadoc ("one slow rating does not hold many locks") describes the intent, not the behaviour. | Claim the batch in one short transaction, then rate each period in its own transaction (`TransactionTemplate` per period or `REQUIRES_NEW`), catch per-period failures, record them (`last_error`, `attempts` on `billing_period`) and continue. Expose a counter `tallyard.billing.failures{step}` and a gauge of overdue periods. |
| F-03 | **High** | O | `deploy/docker-compose.yml` (services `api`, `worker`, `postgres`, `kafka`); `docs/architecture/failure-model.md` §3 row "Worker process dies" | No service has a `restart:` policy. The failure model says recovery is "Restart (Compose `restart` or orchestrator)"; with this file a crashed worker stays down until a human runs `docker compose up`. The chaos drill in `docs/testing/failure-scenarios.md` restarts the worker manually, which hides this. | Add `restart: unless-stopped` to `api`, `worker`, `postgres`, `kafka` (and the observability trio); re-run the kill drill without the manual `up -d worker` step and record that the worker came back on its own. |
| F-04 | **High** | O | `tallyard-app/src/test/java/io/tallyard/credits/CreditLedgerConcurrencyIT.java:55, 92, 119`; `docs/product/acceptance-criteria.md:37`; `docs/testing/testing-strategy.md` coverage table | The only tests of credit application insert an `invoice_draft` shell by raw SQL *before* calling `apply`, i.e. they encode the ordering the production code does not follow, so the suite is green while rating is broken. No integration test rates a customer that has a grant. Acceptance criterion C2 cites `CreditApplicationTest`, which does not exist. The README's "prepaid credits consumed in expiry/priority order" and the walkthrough's demo step 6 ("credits for the prepaid customer") are therefore unverified. | Add the end-to-end prepaid rating IT (see F-01); make `CreditLedgerConcurrencyIT` go through `rateNow` or at least through the same call order as production; fix or remove the `CreditApplicationTest` reference. |
| F-05 | Medium | O | `docs/performance/README.md` §1 table rows "Peak consumer lag", "Consumer batch transaction p50/p99", §2 row "Peak stored rate"; `README.md` Demonstration table; `docs/performance/raw/*` | The document states "raw outputs are in `raw/`. Nothing is extrapolated", but the raw files contain only the simulator stdout, two k6 summaries and a two-line timeline. The numbers 4,142 (peak lag), 41 ms / 192 ms (batch p50/p99), "< 10 s drain" and "2,000 stored events/s on a single worker" come from Grafana/Prometheus and have no exported evidence. `raw/simulator-chaos-run.txt` also prints latencies with a locale decimal comma (`p99=254,5ms`), so the simulator's `%.1f` formatting is locale-dependent. | Export the Prometheus query results used (`curl .../api/v1/query_range` → JSON or CSV) into `raw/`, or label those rows "read from Grafana during the run (no export)". Use `String.format(Locale.ROOT, …)` in `Simulator.java:128,155,312`. |
| F-06 | Medium | O | `README.md:3,5,105,135`; `docs/product/product-specification.md:10-11`; `docs/interview/architecture-defense.md:6`; `docs/interview/walkthrough.md` 30-second pitch; vs `README.md` "Architectural trade-offs" and `docs/architecture/adr/ADR-002` | The tagline is "Billing-grade usage metering that counts exactly once"; the same README later says "Effectively-once, not 'exactly-once' — honest naming". The ADR carefully qualifies "exactly once *in the database*", but the tagline, pitch and defence drop the qualifier. A Kafka-literate interviewer reads this as either confusion or marketing. | Pick one phrase and use it everywhere: "counts each event once — effectively-once (at-least-once delivery + idempotent transactional writes)". Keep "exactly-once" only inside the explicit "not claimed" sentence. |
| F-07 | Medium | O/S | `docs/performance/README.md` §1 row "Rejections recorded"; `README.md` Demonstration row 1; `tallyard-simulator/.../Simulator.java:190-197, 317-334, 394-406` | (O) In the recorded chaos drill all 937 "late" events were rejected `NO_ACTIVE_SUBSCRIPTION` because they predate the 5-second-old demo subscriptions; the late-event/adjustment path advertised in the README headline ("2 % late") was not exercised at all in that run — the performance document admits this in a table cell, the README does not. (S) The truth check compares expected totals only against the *currently open* period; with 10-minute demo periods and a 90-second run, a period roll-over mid-run (probability roughly 15 %) would produce a spurious `MISMATCH`, which is an unpleasant surprise during a live demo. | Start the simulator after the subscriptions are older than the late offset (or make late events land inside a RATED period by rating first), and say so in the README row. Make the truth check sum usage across all periods whose `startsAt >= runStart` or print a clear "period rolled over, comparing N periods" message. |
| F-08 | Medium | O | `tallyard-app/src/main/java/io/tallyard/webhooks/internal/WebhookDispatcher.java:106-115, 128, 143-156, 75-77` | `dispatchBatch` opens a database transaction, locks up to 50 deliveries `FOR UPDATE SKIP LOCKED`, then performs synchronous HTTP POSTs serially inside that transaction. One slow receiver (5 s read timeout, no connect timeout configured on `JdkClientHttpRequestFactory`) holds a Hikari connection and row locks for up to 250 s per batch and delays every other tenant's webhooks. The circuit breaker only opens after 5 calls. | Claim and mark `IN_FLIGHT` in a short transaction, release it, POST outside any transaction (parallel per endpoint, virtual threads are already enabled), then record outcomes in a second short transaction. Set a connect timeout. |
| F-09 | Medium | O | `deploy/docker-compose.yml` (`x-app-env.JAVA_TOOL_OPTIONS`, services `api`/`worker`); `docs/security/security-review.md` §6 "Resource limits: Implemented (local)" | No `mem_limit`/`deploy.resources` on any container. `-XX:MaxRAMPercentage=70` therefore sizes *each* JVM at 70 % of the whole Docker VM (7.7 GiB): two JVMs can legitimately claim ~10.8 GiB on a 7.7 GiB VM alongside Kafka and PostgreSQL. Live idle heaps are already 1.6 GiB (worker) and 1.0 GiB (api). The security review marks resource limits "Implemented". | Add `mem_limit: 1g` (or `deploy.resources.limits`) to `api` and `worker` so `MaxRAMPercentage` has a container ceiling; add `-XX:+ExitOnOutOfMemoryError`; correct the security-review cell. |
| F-10 | Medium | O | `tallyard-app/src/main/resources/application.yml` (`server.shutdown: graceful`, no `spring.lifecycle.timeout-per-shutdown-phase`); `deploy/docker-compose.yml` (no `stop_grace_period`) | Spring's graceful shutdown waits up to 30 s per phase; Compose sends SIGKILL after 10 s. Any in-flight metering batch or webhook POST longer than 10 s is killed mid-way. Correctness survives (idempotent redelivery), but the advertised graceful shutdown never completes. | Set `spring.lifecycle.timeout-per-shutdown-phase: 20s` and `stop_grace_period: 30s` on `api`/`worker`; mention the relationship in `docs/operations/observability.md`. |
| F-11 | Medium | S | `application.yml` (`management.endpoint.health.group.readiness.include: readinessState,db,kafka`); `docs/operations/runbooks.md` R2 "Behaviour" | For the `api` role, readiness drops to DOWN when Kafka is unreachable, so an orchestrator removes the node from the load balancer. Runbook R2 promises that "catalog, usage reads, entitlements, rating and the dashboard continue to work" — they do inside the process, but no client can reach them. The two statements cannot both be true in a real deployment. | Decide: either remove `kafka` from the API readiness group (ingest already answers 503 + `Retry-After`) and keep it for the worker, or rewrite R2 to say the API node leaves rotation. Document the choice in ADR form. |
| F-12 | Medium | O | `tallyard-app/src/main/java/io/tallyard/ingestion/internal/KafkaTopicsConfig.java:22, 31, 41`; `README.md` "202 means durable"; `docs/architecture/architecture.md:302-303` | All three topics are created with `.replicas(1)` hard-coded. "202 means durable" is therefore "durable on one broker disk". The architecture document describes a production shape with replication factor 3, but the code cannot produce it without a change. | Add `tallyard.topics.replication-factor` (default 1 locally), use it in `TopicBuilder`, and set `min.insync.replicas` accordingly; qualify the durability sentence. |
| F-13 | Medium | O | `BillingScheduler.java:43-45`; `docs/operations/runbooks.md` R6; `docs/operations/observability.md` "Alerting suggestions" | A persistent rating failure is logged with a full stack trace every 5 s (102 times in ~9 minutes on the live stack) and is otherwise invisible: there is no counter for scheduler failures, no gauge for overdue periods, and R6 tells the operator to discover the problem by listing periods. The alerting table has no billing alert at all. | Add `tallyard.billing.failures{step}` counter and `tallyard.billing.periods_overdue` gauge (computed in the tick), log the first failure at ERROR and subsequent identical ones at WARN with a suppression window, add an alert row "Billing stalled: `tallyard_billing_periods_overdue > 0 for 10m`" and a dashboard stat. |
| F-14 | Medium | S | `docs/research/*` (`job-market-analysis.md`, `recruitment-evaluation.md`, `15-project-concepts.md`, `final-project-selection.md`), `docs/interview/*`, `README.md` "Documentation index" | The repository ships its own hiring strategy ("define what a public Java 21 repository must demonstrate to pass review by four kinds of readers") and a 48-question interview crib sheet. Technically interesting as process documentation, but it reframes the project as an artefact built to pass screening, and it gives interviewers the candidate's prepared answers to reuse against them. | Move `docs/research` and `docs/interview` to a separate private repository (or a `meta/` branch) and leave a one-line pointer in the README's acknowledgements. Keep ADRs, failure model, runbooks, performance and security review. |
| F-15 | Low | O | `Makefile` targets `openapi` (`-Dit.test=ContractTest`), `.PHONY` line | The OpenAPI refresh target names `ContractTest`; the class is `ContractIT`, so the target runs nothing useful. `infra` and `fmt-check` exist but are not in `.PHONY`. | `-Dit.test=ContractIT`; add the two targets to `.PHONY`. |
| F-16 | Low | O | `.github/workflows/ci.yml` steps "Unit…" and "Integration tests…"; `env.TESTCONTAINERS_RYUK_DISABLED`; `README.md` CI badge; `docs/engineering-progress.md` Phase 11 | `-Dsurefire.skip=true` is not a Surefire property, so the `verify` step re-runs all unit tests after the dedicated unit step. `TESTCONTAINERS_RYUK_DISABLED: "false"` is the default (no-op). The README badge points at a workflow the progress log says has never run ("CI run pending push"). | Run a single `mvn -B verify` step (Surefire then Failsafe) or use `-Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`; drop the no-op env; push and confirm the badge is green before publishing. |
| F-17 | Low | O | `application.yml` (`tallyard.outbox.max-attempts: 20`); `shared/config/TallyardProperties.java:31`; `outbox/internal/OutboxPublisher.java` (never reads `maxAttempts`); `README.md` Configuration table; `.env.example` | `tallyard.outbox.max-attempts` is bound but never used — the publisher retries forever (which is the documented, correct behaviour), so the knob is misleading. The README configuration table omits `TALLYARD_DB_POOL_SIZE`, `TALLYARD_AUTO_FINALIZE_AFTER`, `TALLYARD_DEMO_CUSTOMERS`, `TALLYARD_DEMO_WEBHOOK_URL`, all of which `application.yml` reads. | Delete the unused property or implement it (and document dead-letter semantics for the outbox); add the four variables to the README table and `.env.example`. |
| F-18 | Low | O | `deploy/docker-compose.yml` (`prom/prometheus:latest`, `grafana/grafana:latest`, `postgres:17-alpine`); `deploy/Dockerfile` (`maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre-alpine`) | Floating tags make `make up` non-reproducible; a Grafana major bump can break the provisioned dashboard (`schemaVersion: 39`). Jaeger and Kafka are pinned, so the inconsistency is noticeable. | Pin all images to a minor version (and ideally a digest); add a comment on how to bump. |
| F-19 | Low | O | `deploy/prometheus/prometheus.yml` (`labels: { role: … }`); `application.yml` (`management.metrics.tags.role`); dashboard panel "Consumer lag per partition" | The scrape config and the application both emit a `role` label; Prometheus keeps the scrape label and renames the app's to `exported_role`, so every series carries both (verified). The Kafka client also reports every topic twice (`topic="usage.events.v1"` and `topic="usage_events_v1"`), so the per-partition lag panel shows each partition as two identical lines. | Remove one `role` source (or set `honor_labels: true`); add `topic="usage.events.v1"` to the lag panel selectors. |
| F-20 | Low | O | `tallyard-app/src/main/java/io/tallyard/demo/DemoSeeder.java:218-226`; `docs/security/security-review.md` §4 "Logging" | The seeder logs the full admin API key at INFO. The security review states "API keys are never logged (only prefix via MDC `tenant`)". It is also inaccurate about MDC: `ApiKeyAuthenticationFilter.java:38` puts the tenant UUID, not a key prefix, into MDC. Acceptable for a demo tenant, but the document and the code disagree. | Either keep the log line and amend the security review ("except the demo tenant's key when `TALLYARD_DEMO_ENABLED=true` and no fixed key is configured"), or log only the prefix and tell the user to read the key from `.env`. Fix the MDC sentence. |
| F-21 | Low | O | `metering/internal/UsageEventConsumer.java:99-110` (`kafka.send(dlt)` not awaited) and `:66-91` | Dead-letter publication is fire-and-forget: if the DLT send fails the poison record exists only in `rejected_event`. Because parsing and dead-lettering happen before `metering.process`, a batch retried by the error handler (database down) re-sends the DLT record and re-increments `SCHEMA_INVALID` on every retry attempt, while the rejection row is correctly rolled back. | Collect DLT records and send them after the transaction commits (with `.get(timeout)`), or accept and document the duplicate DLT records under outage. |
| F-22 | Low | O | `docs/engineering-progress.md` ("Completed phases" table: Phase 8 "Pending measurement", 12 "Pending", 13 "Pending", "Open items"); `docs/testing/testing-strategy.md` ("Results" section points to the progress log); `docs/architecture/adr/ADR-002` §4 and `ConsumerRedeliveryIT` | The progress log is stale: README, performance and GitHub phases are marked pending though delivered; the "Open items" list still says "record counts here". Minor overclaim: `ConsumerRedeliveryIT` exercises an in-memory retry by `DefaultErrorHandler` after a thrown hook, not a Kafka redelivery after a consumer restart/rebalance; the documents call it "redelivered". | Update the table and open items; describe the test as "simulated crash between commit and ack (error-handler retry of the same batch)", and keep the manual kill drill as the real redelivery evidence. |
| F-23 | Low | S | `metering/internal/MeteringMetrics.java` (no `@WorkerRole`), `entitlements/internal/EntitlementService.java`, `billing/internal/BillingService.java` timers | Metering, entitlement and rating metrics are registered on both roles, so the API node exports zero-valued `tallyard_metering_*` and the worker exports zero-valued `tallyard_entitlements_*` series (verified). The dashboard's `sum()`/`max()` hide it, but it doubles cardinality and confuses ad-hoc queries. | Gate `MeteringMetrics` with `@WorkerRole`; accept the rest or register lazily. |
| F-24 | Low | S | All `docs/**/*.md` ("Phase N deliverable" headers, single date 2026-10-23), `git log` (entire history on one day), `README.md` acknowledgements | The uniform voice, the "Phase N deliverable" boilerplate at the top of every document, and a 20-commit history all dated the same day make the AI-assisted, single-session origin obvious. The README's honest disclosure is the right call; the boilerplate is not needed and reads as generated. | Remove the "Phase N deliverable" sentences; keep the disclosure; let subsequent fixes (this review's) form a genuine, dated history rather than rewriting the past. |

### What is good (for balance)

- Dashboard queries are correct: all 20 expressions in `deploy/grafana/dashboards/tallyard-operations.json`
  reference metric names and label values that exist on the live scrape (`tallyard_*_total`,
  `*_seconds{quantile}`, `kafka_consumer_fetch_manager_records_lag*`, `hikaricp_*`, `jvm_memory_used_bytes`,
  `http_server_requests_seconds_bucket` with histogram buckets enabled).
- Readiness semantics are real: `readinessState + db + kafka`, 2 s admin-client timeout, Compose waits
  on readiness, worker waits on API (so Flyway runs once first).
- The consumer error-handling design is correct and the opposite of the common mistake: whole-batch
  infrastructure failures retry forever; only unparseable records are dead-lettered, with reason and
  source headers.
- `MeteringService.process` and `UsageEventStore` are exactly the code a Senior interviewer wants to
  read: in-batch dedup, multi-row `INSERT … ON CONFLICT DO NOTHING RETURNING`, deterministic
  lock ordering before the bucket upsert, `LEAST/GREATEST`/`CASE` for MIN/MAX/LAST.
- The security review, failure model and ADRs state non-guarantees explicitly; the k6 raw outputs
  fully support the ingest (p99 22.75 ms, 0 failures) and entitlement (p50 0.27 ms, p99 6.05 ms)
  numbers in the README.
- No TODO/FIXME/`System.out` in application code; `.env` is git-ignored and untracked; Spotless,
  ArchUnit and Modulith checks are wired.

---

## 3. Top 5 fixes before publishing

1. **Fix prepaid rating (F-01) and add the missing end-to-end test (F-04).** Save and flush the
   `InvoiceDraft` before `credits.apply(...)` in `BillingService.rateLocked`, then write
   `BillingLifecycleIT.prepaidCustomerIsRatedWithCreditsApplied` through the public API. Verify on the
   Compose stack that all 20 demo customers reach `RATED` and that the worker log is clean for a full
   period cycle.
2. **Make the billing scheduler fail per period, not per batch, and make failure visible (F-02, F-13).   One transaction per period, `tallyard.billing.failures{step}` counter, overdue-periods gauge,
   a dashboard stat and an alert row; rate-limit the error log.
3. **Harden the Compose stack to match the failure model (F-03, F-09, F-10).** `restart: unless-stopped`,
   `mem_limit` for both JVMs, `stop_grace_period` aligned with `spring.lifecycle.timeout-per-shutdown-phase`.
   Re-run the worker-kill drill without manually restarting the worker.
4. **Make every number and every phrase defensible (F-05, F-06, F-07).** Export the Prometheus data
   behind the lag and batch-latency claims into `docs/performance/raw/`, state that the chaos run's
   "late" events were rejected rather than adjusted (or re-run it so they are adjusted), fix the
   simulator's locale formatting, and replace "counts exactly once" with the effectively-once wording
   everywhere outside the explicit non-guarantee sentence.
5. **Separate the product repository from the job-search material (F-14, F-22, F-24).** Move
   `docs/research` and `docs/interview` to a private repository, update `docs/engineering-progress.md`
   to the real state, remove the "Phase N deliverable" boilerplate, and push so the CI badge is green
   before the link goes on a CV.
