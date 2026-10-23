# Top-Five Concept Comparison

*Scoring date: 2026-10-23. Candidate descriptions are in
[15-project-concepts.md](15-project-concepts.md).*

## Scoring model

| Criterion | Weight | What a 10 means |
|---|---:|---|
| Senior-level engineering depth | 20% | Hard correctness problems (consistency, concurrency, failure) are intrinsic to the core workflow, not bolted on |
| Relevance to actual Java backend requirements | 15% | Matches what Warsaw/EU Java postings ask for: Spring, JPA, Kafka, SQL, testing, transactions (see [job-market-analysis.md](job-market-analysis.md)) |
| Originality and differentiation | 15% | Not a tutorial category; no well-known Java open-source equivalent; memorable after an interview |
| Distributed-systems substance | 15% | Ordering, duplication, partial failure, replay and recovery are *required* for the product to be correct |
| Product usefulness | 10% | Someone could actually run it to solve a problem |
| Architectural decision-making potential | 10% | Many defensible trade-offs with real alternatives |
| Demonstration and presentation quality | 5% | A 10-minute live demo with visible state change and a controlled failure |
| Testing and measurable performance | 5% | Deterministic core; a meaningful load profile with latency/throughput targets |
| Long-term extensibility | 5% | Natural roadmap (new pricing models, multi-region, new data sources) |

Each score is 0–10. Weighted score = Σ(score × weight).

## Scores

| Criterion (weight) | 1 Tallyard | 2 Gridflex | 3 Coldline | 4 Reconcilio | 9 Capex |
|---|:-:|:-:|:-:|:-:|:-:|
| Engineering depth (20%) | 9 | 7 | 7 | 7 | 7 |
| Java market relevance (15%) | 9 | 6 | 6 | 9 | 6 |
| Originality (15%) | 8 | 8 | 9 | 5 | 8 |
| Distributed-systems substance (15%) | 9 | 7 | 7 | 5 | 7 |
| Product usefulness (10%) | 8 | 7 | 7 | 7 | 3 |
| Architectural decisions (10%) | 9 | 8 | 7 | 6 | 7 |
| Demo quality (5%) | 8 | 7 | 8 | 5 | 6 |
| Testing & performance (5%) | 9 | 6 | 7 | 7 | 8 |
| Extensibility (5%) | 8 | 7 | 6 | 7 | 5 |
| **Weighted total** | **8.70** | **7.05** | **7.15** | **6.55** | **6.50** |

Weighted totals: Tallyard = 0.2·9+0.15·9+0.15·8+0.15·9+0.1·8+0.1·9+0.05·8+0.05·9+0.05·8 = 8.70;
Gridflex = 7.05; Coldline = 7.15; Reconcilio = 6.55; Capex = 6.50.

## Score justifications

### 1. Tallyard — usage metering, rating and entitlement engine

- **Depth 9.** The product is *wrong* unless duplicates are suppressed, late events follow a stated
  policy, period closing is atomic, rating is deterministic and prepaid balances never go negative under
  concurrency. These are precisely the senior-level problems (idempotency, at-least-once + idempotent
  writes, state machines, optimistic/pessimistic locking, replay) — and they are unavoidable here.
  Not a 10 because the system is single-region and does not need consensus.
- **Relevance 9.** Finance-adjacent infrastructure (metering → invoicing) matches the fintech, banking
  and insurance employers that dominate Warsaw Java hiring; the stack is exactly Spring Boot + JPA +
  Kafka + PostgreSQL + Testcontainers.
- **Originality 8.** The category is well known commercially (Stripe Meters, OpenMeter, Lago, Orb) but
  there is no modern open-source Java implementation; a reviewer will remember "the metering engine
  that survives duplicate and late events". Not higher because the *category* is not novel.
- **Distributed substance 9.** Partitioned ordering per customer, consumer restarts, duplicate delivery,
  outbox, DLQ, replay are intrinsic.
- **Usefulness 8.** A small SaaS could actually run it for internal metering.
- **Architectural decisions 9.** Dozens of real trade-offs: dedup in DB vs. stream store; closing
  windows vs. immutability; hot-path entitlement consistency; modular monolith vs. services; JPA vs.
  JDBC on the hot path; partitioning strategy.
- **Demo 8.** Live counters, deliberate duplicates, kill-and-restart, byte-identical replay. Not 10
  because the visuals are tables and counters rather than something inherently graphic.
- **Testing/perf 9.** Rating is pure and property-testable; ingestion has an obvious load profile
  (events/s, p99, consumer lag); entitlement check has a latency target.
- **Extensibility 8.** New aggregations, pricing models, currencies, webhooks, multi-region dedup.

### 2. Gridflex — DER flexibility dispatch

- **Depth 7.** Dispatch and settlement are nontrivial, but much of the hardness is in optimisation and
  domain rules rather than distributed correctness.
- **Relevance 6.** Energy is a smaller Java employer segment in the target market.
- **Originality 8.** Memorable and topical.
- **Distributed substance 7.** Device telemetry and sagas are real; strict consistency needs are weaker.
- **Usefulness 7.** Only with real devices or a convincing simulator.
- **Decisions 8.** Good trade-off surface.
- **Demo 7.** Simulated devices undermine credibility; PSE data integration adds an external dependency
  whose format and stability could not be fully verified.
- **Testing/perf 6.** Dispatch optimisation is hard to benchmark meaningfully.
- **Extensibility 7.**

### 3. Coldline — cold-chain excursion control

- **Depth 7.** Stateful stream processing and MKT calculation are interesting; consistency requirements
  are modest (no money, no balances).
- **Relevance 6.** Logistics/pharma is a narrower Java segment.
- **Originality 9.** The most memorable concept; no open-source equivalent found.
- **Distributed substance 7.** Out-of-order sensors, workflows; less need for strict guarantees.
- **Usefulness 7.** Realistic for a 3PL, but stability-budget data per product is proprietary.
- **Decisions 7.** Fewer hard forks in the road.
- **Demo 8.** Visual (temperature curves, quarantine).
- **Testing/perf 7.** MKT is testable; load story is weaker.
- **Extensibility 6.**

### 4. Reconcilio — payment reconciliation

- **Depth 7.** Matching logic and period close are real; largely batch.
- **Relevance 9.** Highly aligned with bank/fintech employers.
- **Originality 5.** Reads as a "banking app"; many internal implementations exist.
- **Distributed substance 5.** Kafka would be optional; the process is file-driven.
- **Usefulness 7.**, **Decisions 6.**, **Demo 5.** (files in, breaks out), **Testing/perf 7.**,
  **Extensibility 7.**

### 9. Capex — capacity reservation exchange

- **Depth 7.** Concurrency on scarce resources is good material.
- **Relevance 6.** Platform-engineering flavour; weaker Spring/JPA story.
- **Originality 8.** Unusual.
- **Distributed substance 7.**
- **Usefulness 3.** No clear real user; the demo is a game.
- **Decisions 7.**, **Demo 6.**, **Testing/perf 8.**, **Extensibility 5.**

## Sensitivity check

To make sure the result is not an artefact of the weights, the two most subjective criteria were
varied:

- Doubling the weight of *Originality* (to 30%, taking 15% from depth) gives Tallyard 8.55, Coldline
  7.45, Gridflex 7.20 — ranking unchanged.
- Zeroing *Java market relevance* (redistributed to depth) gives Tallyard 8.65, Coldline 7.30, Gridflex
  7.20 — ranking unchanged.

Tallyard wins under every reasonable weighting because it is the only candidate where distributed
correctness, database/concurrency rigour and measurable performance are all *forced* by the product
definition rather than added for show.
