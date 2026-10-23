# ADR-004: Deterministic rating with reproducibility proof by replay

**Status:** Accepted (2026-10-23)

## Context

Finance and customers ask "why is this line $137.50?" months later. The answer must be derivable
from raw events and the plan, not from a snapshot nobody can re-check.

## Decision

- `RatingEngine` is a pure function `(plan, quantities, adjustments) → lines + subtotal + hash` with
  no clock, I/O or randomness (enforced by an ArchUnit rule). Money is exact to six decimals during
  computation; each line is rounded HALF_EVEN to the currency's minor unit; the subtotal is the sum of
  rounded lines.
- Each line stores a `breakdown` JSON (tiers entered, units per tier, unit price, flat fees,
  packages) so the number is explainable without recomputation.
- The invoice stores a SHA-256 `reproducibility_hash` over a canonical rendering of the lines.
- Two usage sources exist behind one SPI (`billing.UsageSource`):
  - **buckets** — hourly pre-aggregates, used when rating (fast);
  - **event log** — recomputed with SQL that mirrors the consumer's filters (acceptance window,
    plain-decimal grammar) over the events whose recorded `period_id` is this period. The consumer
    writes that membership in the same transaction as the buckets (migration `V2`), so replay is
    independent of consumer lag and wall-clock timestamps; late events carry no membership.
- `POST /v1/periods/{id}/replay` re-derives quantities from the log, re-rates, and compares lines and
  hash with the stored invoice. `matches=true` is a proof that buckets, log and rating agree; a diff
  points at exactly which line drifted.

## Consequences

- Any bug in bucket maintenance surfaces as a replay mismatch rather than a silent wrong invoice —
  the test suite tampers with a bucket to prove the detector works.
- The Java value grammar and the SQL regex must stay in sync; both are documented next to each
  other and covered by `ValueExtractorTest` / `BillingLifecycleIT`.
- Replay reads the full event set for one customer-period; for very large periods this is a range
  scan on `(tenant_id, subject, occurred_at)`. Acceptable for an audit operation; not on a hot path.
