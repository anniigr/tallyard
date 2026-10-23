# Architecture Defence

*Phase 14 deliverable. Direct answers to the questions a Staff engineer asks.*

**Why this architecture?**
Because the product's correctness properties — exactly-once counting, atomic settlement, exact rating,
non-negative balances — are all enforceable by one relational database with constraints and row locks
inside single transactions. A modular monolith with verified boundaries gives that with the least
machinery; Kafka is added only where asynchrony is genuinely required (durable ingest buffer with
replay and backpressure; fan-out of billing events).

**Why these technologies?**
Spring Boot 4 / Java 21 because they are the current GA line of the ecosystem the target roles use;
PostgreSQL because `ON CONFLICT … RETURNING`, partial unique indexes, `FOR SHARE/UPDATE`,
`SKIP LOCKED` and `jsonb` do real work here; Kafka for ordered, durable, replayable transport. Every
other dependency has a one-line justification in `technology-decisions.md`, and eleven candidates
were rejected with reasons.

**What alternatives were rejected?**
Microservices (sagas for weaker guarantees), Kafka Streams aggregation (second source of truth),
Redis dedup (non-transactional), Schema Registry (one topic, one producer), Temporal/Camunda (a
four-state machine does not need an engine), event-sourcing the catalog, Kubernetes manifests that
would be untested, LLM features (non-deterministic core).

**What was the most difficult technical problem?**
Late events versus settlement versus provability. Settling a period must not lose usage; late usage
must still be billed; an invoice must remain immutable and reproducible. The solution has three
parts: a lock protocol so usage never lands in a settled period, adjustments carried into the open
period and priced marginally, and replay that reads the log as of the rating time. Each part alone
is simple; the interaction is where bugs live, so it has dedicated integration tests.

**Where could the system fail?**
- Single PostgreSQL primary: throughput ceiling and availability dependency.
- Clients that do not reuse event ids on retry: double counting the system cannot detect.
- Hot customers: one partition each, so one very large customer bounds consumer parallelism.
- Entitlement staleness: up to `cache-ttl` of over-consumption on hard limits.
- Operators force-rating periods early: supported, but the successor period starts in the future and
  the code must (and does) look up the open period by state.

**What consistency guarantees exist?**
Transactional within PostgreSQL; at-least-once across Kafka hops with idempotent sinks; bounded
staleness (2 s) for entitlements; exact reproducibility of invoices from raw events (hash-verified).
Written down in `failure-model.md` with the non-guarantees next to them.

**What is the main scalability bottleneck?**
Write throughput on `usage_event` (unique index probes + inserts) and bucket upsert contention for
hot customers, both on one primary. Measured numbers are in `docs/performance`; the path is
partitioning (monthly ranges, period-hash buckets), then tenant sharding — possible because no
transaction spans tenants.

**How would the architecture change under 100× load?**
API tier scales horizontally today. Workers scale to the partition count; raise partitions. Move
dedup + bucket writes to tenant shards (each shard its own PostgreSQL), keep Kafka as the single
ingress with a tenant-aware partitioner. Replace in-process entitlement snapshots with a shared cache
invalidated by the consumer. Nothing in the module structure changes; the deployment topology does.

**What would you redesign after six months in production?**
Introduce the partitioning early rather than late; add a tenant-level event retention policy;
version the encryption key per row to allow rotation; add SSRF protection for webhook URLs; possibly
split the dashboard aggregator out of the API role if its queries contend with ingest.

**Which improvements would not justify their cost?**
Kafka transactions (no exactly-once gain for a PostgreSQL sink), Avro + Schema Registry (one schema),
a workflow engine, CQRS read store, Kubernetes for the demo, microservices.

**What trade-offs were consciously accepted?**
- Bounded entitlement staleness over read-your-writes.
- Per-node rate limiting over a global limiter.
- Hand-rolled outbox over Spring Modulith's registry (explicitness over convenience).
- JDBC on the hot path over uniform JPA (performance and set-oriented SQL over consistency of style).
- Monthly partitioning deferred (documented) to keep the first schema readable.
- A demo webhook sink and a dashboard aggregator inside the application, clearly labelled as
  demonstration layers.
