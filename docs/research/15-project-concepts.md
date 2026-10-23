# Fifteen Candidate Product Concepts

*Phase 3 deliverable. Research date: 2026-10-23. Scoring model and the final choice are in
[top-five-comparison.md](top-five-comparison.md) and [final-project-selection.md](final-project-selection.md).*

## Purpose and constraints

The goal of this exercise was to find a product concept that is **memorable, genuinely useful, and
technically deep enough** to justify senior-level Java backend engineering: real distributed-systems
problems (ordering, duplication, partial failure, recovery), real database and concurrency problems,
real performance questions, and a domain with enough rules to be worth modelling.

Every concept below was checked against the same questions before scoring:

- Does a recognisable real-world problem exist, and who has it?
- What comparable products or open-source repositories already exist?
- What data is available to drive a convincing local demonstration?
- Can the defining feature be built and demonstrated on one laptop with Docker?
- What are the architectural and legal/licensing risks?
- How testable is the core logic, and how measurable is performance?

Concepts that failed the feasibility check are kept in the list with the reason for rejection, because
the rejections are as informative as the selection.

Overused categories (todo apps, generic e-commerce, basic banking, booking, inventory, chat, Petclinic
clones, URL shorteners, thin LLM wrappers) were excluded upfront; see
[anti-patterns.md](anti-patterns.md).

---

## Concept list

### 1. Tallyard — usage metering, rating and entitlement engine *(selected)*

**Pitch.** Billing-grade usage metering for software products: ingest millions of usage events,
deduplicate them, aggregate them into meters with an explicit late-event policy, rate them against
pricing plans (tiered, volume, package, included quantities, prepaid credits), enforce real-time
entitlements, and produce auditable, reproducible invoice line items — correct under retries, crashes
and replays.

**Problem.** Every SaaS/API company that charges per unit (tokens, API calls, GB, seats, messages) has
to count usage *exactly once* at scale and turn it into money. Getting this wrong means over-charging
customers or losing revenue. Stripe Billing Meters, OpenMeter (Go), Lago (Ruby), Orb and Metronome
exist; there is no comparable modern open-source implementation in Java/Spring.

**Why it is deep.** Idempotent at-least-once ingestion; per-customer ordering via Kafka partitioning;
late and out-of-order events with period-closing semantics (watermarks, grace windows, adjustments
into the next open period); deterministic money arithmetic; period lifecycle state machine
(OPEN → CLOSING → RATED → FINALISED); prepaid credit ledger where balances must never go negative
under concurrent consumption; a low-latency entitlement check that must stay consistent with the
ledger; deterministic replay to rebuild aggregates from the immutable event log; transactional outbox
for downstream billing events and webhooks.

**Demo.** A simulator streams events for several customers (including deliberate duplicates and late
events); a dashboard shows live meters, period state and draft invoice lines; the operator kills the
worker mid-batch, restarts it, and totals are provably unchanged; the period is closed and the invoice
is reproduced byte-for-byte by replay.

**Data.** Synthetic but realistic (generated usage profiles). No licensing issues.

**Risks.** Rating rules can sprawl — scope must be fixed to a defined pricing model set. Entitlement
hot-path consistency is the hardest design question and must be stated honestly (bounded staleness).

### 2. Gridflex — distributed energy resource flexibility dispatch

**Pitch.** Coordinate batteries, EV chargers and flexible loads to answer grid balancing requests:
receive an activation request, select resources, dispatch, verify delivered flexibility from telemetry,
and settle.

**Problem.** Real (balancing markets, demand response). PSE (Polish TSO) publishes load and generation
data through `api.raporty.pse.pl` (API v2, OData-style filters; verified 2026-10-23), which could seed
a realistic demand curve.

**Deep parts.** Dispatch optimisation, telemetry stream processing, settlement with baselines,
sagas for activation/cancellation, device unreliability.

**Risks.** Domain rules (baselines, settlement) are complex and jurisdiction-specific; the defining
feature depends on simulated devices, which weakens the "real" feel. Senior reviewers from fintech
would find the domain unfamiliar; the PSE API format and stability were not fully verifiable.

### 3. Coldline — cold-chain excursion control for pharmaceuticals

**Pitch.** Ingest temperature telemetry per shipment, compute cumulative excursion budgets and Mean
Kinetic Temperature (Arrhenius-based, as in the Vaisala and USP guidance), quarantine lots on breach,
and run an investigation/release workflow.

**Problem.** Real (GDP compliance). No dedicated open-source MKT/excursion engine found; only a
ClickHouse SQL example (OneUptime, 2026) and commercial modules.

**Deep parts.** Complex event processing, out-of-order sensor data, stateful per-shipment windows,
workflow with compensations, audit trail.

**Risks.** Memorable, but the hard rule set (stability budgets per product) requires domain data that
is not public; the distributed-systems content is thinner than in 1 (no money, weaker consistency
requirements). Less aligned with the fintech/insurance-heavy Warsaw Java market.

### 4. Reconcilio — payment reconciliation engine

**Pitch.** Match internal ledger entries against PSP/bank settlement files (CAMT.053-style), surface
breaks, drive exception workflows, and close accounting periods.

**Deep parts.** Matching algorithms (1:1, 1:n, tolerance), idempotent file ingestion, period close.

**Risks.** Highly relevant to banks and fintechs, but reads as "banking application" to recruiters;
sample settlement files must be synthetic; the real-time/Kafka component is weaker (batch-oriented).

### 5. Rollgate — progressive delivery controller with SLO-based gates

**Pitch.** Orchestrate canary rollouts across environments; gate promotion on metrics (error rate,
latency) read from Prometheus; roll back automatically.

**Deep parts.** Durable multi-step workflows, timeouts, compensations, metric evaluation.

**Risks.** Argo Rollouts and Flagger already do this well; a Java implementation without a Kubernetes
cluster becomes a simulation of a simulation. Low product usefulness outside Kubernetes.

### 6. Pulsecommand — alert correlation and incident response

**Pitch.** Ingest alerts, deduplicate and correlate into incidents, escalate via on-call schedules,
track acknowledgements and post-mortems.

**Risks.** Crowded space (Keep, Grafana OnCall, Oncall by LinkedIn, Dispatch by Netflix); the
distinctive engineering (correlation rules) is hard to make convincing with synthetic alerts.

### 7. Slotwise — airport slot and gate allocation

**Pitch.** Allocate gates/stands to flights with constraint rules, re-plan on delays, publish changes.

**Risks.** Interesting constraint solving, but real schedules are licensed (OAG, Eurocontrol); the
backend problems are mostly optimisation rather than distributed correctness.

### 8. Transitshift — real-time transit disruption re-planning

**Pitch.** Consume GTFS-Realtime feeds, detect disruptions, compute alternative itineraries.

**Risks.** Overlaps with the author's existing `Mobility-pulse` repository; routing engines (OTP,
MOTIS) dominate; Kafka would be decorative.

### 9. Capex — cloud capacity reservation exchange

**Pitch.** A marketplace where internal teams reserve compute capacity; supports oversubscription,
preemption, bidding and quotas.

**Deep parts.** Concurrency on scarce resources, allocation algorithms, event-driven preemption.

**Risks.** Fun, but there is no obvious real user; the demo is an abstract game. Weak product
usefulness.

### 10. Carbonledger — carbon credit registry with double-counting prevention

**Pitch.** Issue, transfer and retire carbon credits with cryptographic provenance and conflict
detection across registries.

**Risks.** Credible domain (Verra, Gold Standard), but the interesting part is governance, not
engineering; synthetic data; "blockchain-adjacent" perception.

### 11. Trialkit — clinical trial randomisation and drug supply (IRT)

**Pitch.** Stratified/block randomisation, drug kit allocation, resupply triggers, blinding.

**Deep parts.** Strong invariants (blinding, kit uniqueness), concurrency on kit allocation.

**Risks.** Narrow audience; hard to make a general engineering reviewer care; regulatory expectations
(21 CFR Part 11) impossible to demonstrate honestly.

### 12. Quotaforge — distributed rate limiting and quota service

**Pitch.** Multi-tenant rate limiting as a service with hierarchical quotas and distributed token
buckets.

**Deep parts.** Hot-path latency, consistency vs availability trade-offs.

**Risks.** Essentially a subset of concept 1 (entitlements) without the domain; Bucket4j and Envoy
rate-limit already exist; harder to demonstrate "product value".

### 13. Provenance Graph — data lineage and provenance tracking

**Pitch.** Capture dataset lineage events from pipelines, build a provenance graph, answer impact
queries.

**Risks.** OpenLineage/Marquez dominate; graph querying pulls toward Neo4j rather than PostgreSQL,
weakening the alignment with the target stack.

### 14. Tollhouse — congestion-charging clearing house

**Pitch.** Ingest licence-plate/transponder passages, deduplicate across gantries, price trips,
aggregate into statements, handle disputes.

**Deep parts.** Dedup across sensors, late events, pricing — structurally close to concept 1 but
domain-specific.

**Risks.** Narrow domain; synthetic plate data only; concept 1 covers the same engineering with a
wider audience.

### 15. Featurestage — feature-flag and configuration rollout with consistent snapshots

**Pitch.** Serve feature flags with versioned snapshots, staged rollouts and audit trail.

**Risks.** Unleash, Flagsmith, OpenFeature exist; distributed-systems content is modest; mostly a
CRUD-plus-cache service.

### 16. Sealedbid — combinatorial sealed-bid auction clearing *(bonus concept)*

**Pitch.** Run sealed-bid auctions with combinatorial bids and winner determination.

**Risks.** Winner determination is NP-hard and the interesting part is the solver, not the backend.

---

## Feasibility summary

| # | Concept | Real problem | Public data | Laptop demo | Testable core | Perf story | Main risk |
|---|---------|:-:|:-:|:-:|:-:|:-:|---|
| 1 | Tallyard | Yes | Synthetic (fine) | Yes | Excellent | Strong | Scope of rating rules |
| 2 | Gridflex | Yes | Partial (PSE) | Simulated devices | Good | Medium | Domain complexity, API uncertainty |
| 3 | Coldline | Yes | None public | Simulated sensors | Good | Medium | Domain data not public |
| 4 | Reconcilio | Yes | Synthetic | Yes | Good | Weak (batch) | "Banking app" perception |
| 5 | Rollgate | Yes | n/a | Needs Kubernetes | Medium | Weak | Argo/Flagger exist |
| 6 | Pulsecommand | Yes | Synthetic | Yes | Medium | Medium | Crowded |
| 7 | Slotwise | Yes | Licensed | Partial | Medium | Weak | Data licensing |
| 8 | Transitshift | Yes | GTFS-RT | Yes | Medium | Medium | Overlap with existing repo |
| 9 | Capex | Weak | n/a | Yes | Good | Strong | No real user |
| 10 | Carbonledger | Yes | Synthetic | Yes | Medium | Weak | Governance > engineering |
| 11 | Trialkit | Yes | None | Yes | Good | Weak | Narrow audience |
| 12 | Quotaforge | Yes | n/a | Yes | Good | Strong | Subset of 1 |
| 13 | Provenance Graph | Yes | Synthetic | Yes | Medium | Weak | Graph DB pull |
| 14 | Tollhouse | Yes | Synthetic | Yes | Good | Strong | Narrow version of 1 |
| 15 | Featurestage | Yes | n/a | Yes | Medium | Weak | CRUD-plus-cache |
| 16 | Sealedbid | Partial | n/a | Yes | Good | Medium | Solver, not backend |

The five concepts advanced to weighted scoring were **1, 2, 3, 4 and 9** (9 was kept over 12/14
because it is the most different engineering shape; 12 and 14 are structurally subsumed by 1).

## Sources consulted for feasibility

- Stripe Billing Meters documentation: <https://docs.stripe.com/billing/subscriptions/usage-based/recording-usage-api>
  (idempotency identifiers, 35-day timestamp acceptance window, sum/count/last aggregation, pre-aggregated windows).
- OpenMeter architecture and deduplication posts: <https://openmeter.io/docs/open-source/architecture>,
  <https://openmeter.io/blog/usage-deduplication> (Kafka → sink worker → ClickHouse; 32-day dedup window; why
  ReplacingMergeTree is insufficient).
- Lago event ingestion and rating engine architecture: <https://getlago.com/blog/event-ingestion-architecture>,
  <https://getlago.com/blog/rating-engine-architecture> (graduated vs. volume tiers).
- Usagebox: idempotent metering and late arrivals: <https://usagebox.com/articles/idempotent-usage-metering-dedup-late-events>.
- PSE API v2 community guide: <https://prcwiek.quarto.pub/r-random-code/posts/wind_solar_generation_Poland_reviewed/wind_solar_generation_Poland_reviewed.html>.
- Vaisala, Mean Kinetic Temperature application note (MKT definition): <https://www.vaisala.com/sites/default/files/documents/CEN-G-Mean-Kinetic-Temperature-Application-Note-B211534EN-A-FINAL.pdf>.
- OneUptime, cold-chain MKT in ClickHouse (2026-03-31): <https://oneuptime.com/blog/post/2026-03-31-clickhouse-cold-chain-environmental-conditions/markdown>.
