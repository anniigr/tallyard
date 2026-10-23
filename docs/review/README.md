# Independent Review — Outcome

Two adversarial reviews were run against the repository on 2026-10-23, each by a reviewer with no
prior context, instructed to read the code rather than the documentation and to separate objective
defects from opinions:

| Report | Reviewer | Score | Findings |
|---|---|---|---|
| [review-engineering-and-architecture.md](review-engineering-and-architecture.md) | A — Senior Java Engineer | 6.5 / 10 | 31 (2 Critical, 4 High, 13 Medium, 12 Low) |
| | B — Staff Software Architect | 6.0 / 10 | |
| [review-operations-and-hiring.md](review-operations-and-hiring.md) | C — Site Reliability Engineer | 5.5 / 10 | 24 (1 Critical, 3 High, 10 Medium, 10 Low) |
| | D — Technical Hiring Manager | 7.0 / 10 | |

The scores are kept as given. Both reviews independently found the same critical defect, which the
test suite had not caught because the only credit test inserted an invoice shell by hand — the kind
of blind spot that justifies independent review.

## Disposition of findings

| Finding | Severity | Disposition |
|---|---|---|
| TY-01 / F-01 — credit ledger written before the invoice row (FK violation; every prepaid customer un-invoiceable) | Critical | **Fixed.** Invoice persisted first, credits applied on the managed instance; new end-to-end IT `prepaidCustomerIsRatedWithCreditsAppliedThroughThePublicApi` asserts the persisted invoice. Live stack re-verified: 20/20 periods rated, prepaid invoices show credits, 0 scheduler failures |
| TY-02 / F-02 / F-13 — one failing period stalled a 25-period scheduler transaction forever | Critical / High | **Fixed.** Each period is processed in its own transaction; `tallyard.billing.failures` counter and `overdue_periods` gauge; Grafana stat and alert suggestion |
| TY-03 / TY-04 — replay "as of `rated_at`" depends on consumer lag | High | **Fixed.** Counted events record their `period_id` (migration V2); replay reads by membership. Lag at rating time now shifts usage to the next period as adjustments (documented in ADR-003) rather than producing false mismatches |
| TY-05 — webhook consumer acked after swallowing DB failures | High | **Fixed.** Only parse failures are skipped; infrastructure failures propagate to the retrying error handler |
| TY-06 — negative caching rejected events sent right after onboarding | High | **Fixed.** Misses are no longer cached on the metering path |
| F-03 / F-09 / F-10 — no restart policy, memory limits or shutdown grace in Compose | High / Medium | **Fixed.** `restart: unless-stopped`, `mem_limit: 1g`, `stop_grace_period: 30s`, Spring phase timeout 20 s |
| F-04 / TY-16 — credit tests bypassed the public path; acceptance criteria cited non-existent tests | High / Medium | **Fixed.** New IT through the API; acceptance criteria now reference real test classes |
| TY-08 — late MIN/MAX/LAST events priced additively | Medium | **Fixed.** Only SUM/COUNT carry forward; others rejected with `LATE_NOT_ADJUSTABLE` (tested) |
| TY-09 — caught `DataIntegrityViolationException` inside an open JPA transaction | Medium | **Fixed.** `INSERT … ON CONFLICT DO NOTHING` |
| TY-10 — webhook endpoint cache never invalidated | Medium | **Fixed.** 30 s TTL cache |
| TY-14 / F-21 — dead-letter publish fire-and-forget | Medium / Low | **Fixed.** Publish awaited; failure fails the batch |
| TY-19 — `CreditLedger.apply` not MANDATORY | Medium | **Fixed.** |
| TY-20 / TY-21 — odd overload; lock before tenancy check | Low | **Fixed.** |
| F-05 / F-07 — performance numbers without raw series; chaos "late" events were rejections | Medium | **Fixed.** Prometheus series exported to `docs/performance/raw/`; README and report state what happened |
| F-06 — "counts exactly once" tagline vs. effectively-once | Medium | **Fixed.** Tagline reworded |
| F-15 / F-16 / F-22 / F-24 — Makefile target, CI double-run, stale progress log, boilerplate | Low | **Fixed.** |
| F-17 — unused `outbox.max-attempts` | Low | **Fixed.** Property removed (retries are unbounded by design) |
| TY-11 / F-08 — webhook HTTP calls inside the claiming DB transaction | Medium | **Accepted for now.** Bounded by batch size × timeout; the fix (claim → send → update in separate transactions) is the first item of the backlog |
| TY-07 — outbox ordering per aggregate with several publishers | Medium | **Documented** as a non-guarantee in the failure model |
| TY-12 / TY-13 — entitlement snapshot miss cost; dashboard overview N+1 | Medium | **Accepted.** Miss cost is bounded and measured (p99 6 ms at 500 rps); the overview is a demonstration endpoint |
| TY-15 — unbounded growth of outbox/delivery/rejection tables | Medium | **Documented** (retention in runbooks); partitioning/retention jobs are on the roadmap |
| TY-17 — batches larger than the per-second limit can never pass | Medium | **Documented**: `max-batch-size` (1000) must stay ≤ the rate limit (default 5000) |
| TY-18 — LAST tie-break differs between buckets and log on equal `occurred_at` | Medium | **Documented** limitation (ties resolved by arrival order in buckets, by `ingested_at` in the log) |
| F-11 — API readiness drops when Kafka is down although reads still work | Medium | **Accepted trade-off**: an API node that cannot ingest should leave the pool; documented |
| F-12 / TY-25 — topics created with replication factor 1 | Medium / Low | **Documented**: local default; production sets the factor at the broker |
| F-14 — research/interview material inside the product repository | Medium (opinion) | **Kept**: deliberate for this portfolio repository; the README separates product documentation from the preparation material |
| F-20 — seeder logs the demo admin key | Low | **Accepted** for the demo tenant only (`TALLYARD_DEMO_ENABLED=false` in production) |
| TY-22, TY-23, TY-24, TY-26 … TY-31, F-18, F-19, F-23 | Low | Backlog; see the reports for details |

## What changed in the scores

The reviews were not re-run after the fixes; the scores above reflect the state they examined. The
fixes are verified by the test suite (`mvn verify`) and by re-running the Compose stack and the
simulator (see `docs/engineering-progress.md`).
