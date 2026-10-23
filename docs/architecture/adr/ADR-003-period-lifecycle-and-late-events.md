# ADR-003: Period lifecycle, locking protocol and late-event policy

**Status:** Accepted (2026-10-23)

## Context

A billing period must at some point stop accepting usage so an invoice can be produced, yet events
legitimately arrive late (offline SDKs, retries, batch exports). Two concurrent actors touch a
period: the metering worker writing usage into it and the billing scheduler settling it.

## Decision

### State machine

`OPEN → CLOSING → RATED → FINALIZED`, irreversible, enforced by `BillingPeriod` and a `@Version`
column. `OPEN` and `CLOSING` accept usage; `RATED` and `FINALIZED` do not. `CLOSING` is the grace
window after `endsAt` during which stragglers still land in the period. Exactly one `OPEN` period per
subscription is enforced by a partial unique index (`billing_period_one_open`).

### Locking protocol

- Metering takes `SELECT … FOR SHARE` on the period row(s) it writes into (`lockPeriodCovering`),
  inside its batch transaction.
- Rating takes `SELECT … FOR UPDATE` on the period (`lockForUpdate`), which waits for in-flight
  metering transactions and blocks new ones until it commits.
- Therefore either a batch commits before rating (and is rated) or it starts after rating (and sees
  `RATED`, so its events become adjustments). There is no window in which usage is written into a
  period that is being settled.
- Schedulers across worker instances claim due periods with `FOR UPDATE SKIP LOCKED`; `@Version` is
  the second line of defence (`BillingLifecycleIT.concurrentRatingProducesExactlyOneInvoice`).

### Late-event policy

For an event whose `occurredAt` falls in a settled period:

1. within the meter's acceptance window → stored in the log and recorded as a `usage_adjustment`
   against the subscription's current `OPEN` period, referencing the original period;
2. older than the acceptance window → rejected with `OUTSIDE_ACCEPTANCE_WINDOW`;
3. `UNIQUE_COUNT` meters cannot be adjusted additively → rejected with a reason, kept in the log.

Adjustments are priced marginally when the open period is rated: `rate(quantity + adjustment) −
rate(quantity)` under the current component, shown as a separate `ADJUSTMENT` line with its own
breakdown. Buckets of settled periods are never modified.

### Why not re-open or re-rate?

Re-rating a period after an invoice exists destroys auditability and breaks downstream systems that
already consumed `invoice.drafted`. Carrying corrections forward is the industry norm (credit notes,
adjustment lines) and keeps every invoice immutable and reproducible.

## Consequences

- Replay of a settled period must read the log *as of* the rating time (ADR-004); otherwise late
  events would make a correct invoice look wrong.
- Rating waits for in-flight metering batches on the same period — bounded by one batch duration
  (tens of milliseconds).
- Operators can force `rate` on an `OPEN` period (demo/incident use); the successor opens at the
  original end time, so an open period may start in the future. Lookups of "the open period" must
  therefore go by state, not by time (`lockOpenPeriod`).
