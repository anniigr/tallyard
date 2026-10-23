# ADR-005: Entitlement decisions with a bounded, explicit staleness

**Status:** Accepted (2026-10-23)

## Context

`POST /v1/entitlements/check` sits on the caller's request path and must answer in milliseconds at
hundreds of requests per second. A perfectly consistent answer would require reading buckets (and,
for prepaid customers, rating the open period) on every call.

## Decision

- A snapshot per `(tenant, subject, meter)` — current usage, hard limit, estimated remaining credit —
  is computed from the database on a cache miss and cached in-process for
  `tallyard.entitlements.cache-ttl` (default 2 s).
- Decisions are arithmetic on the snapshot: `used + requested ≤ limit`, `creditRemaining > 0`.
- The TTL **is** the staleness bound and is stated in the API documentation and the response
  (`asOf`). Usage counted in the last TTL may not yet affect a decision.
- Cache misses are counted (`tallyard.entitlements.snapshot_misses`) so operators can see hit rates.

## Alternatives

- **Read-your-writes** (no cache): correct but every check costs several queries plus a rating;
  p99 would be dominated by database latency under load.
- **Shared cache (Redis) invalidated by the consumer**: consistent across API nodes within the
  consumer's latency; adds a dependency and a failure mode. Documented as the upgrade path when API
  nodes are many and limits are tight.
- **Reservations** (decrement a counter at check time): turns a read into a write per request and
  requires reconciliation with the metered truth. Not justified for soft limits.

## Consequences

- Latency target (p99 < 20 ms at 500 req/s warm) is achievable on a laptop; measured results live in
  `docs/performance`.
- Customers can exceed a hard limit by at most `TTL × request rate` units. Acceptable for API quotas;
  not acceptable for, e.g., spending caps in a casino — which is why the trade-off is written down.
