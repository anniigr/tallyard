# Ownership Checklist

*Before presenting this repository, be able to do each of the following without notes. The point is
not to recite; it is to have actually run, read and modified the code. Do not claim hands-on
experience with anything on this list you cannot demonstrate on the spot.*

## Run it

- [ ] `make up` from a clean clone, log in to the dashboard with the demo key, run `make sim`, kill
      and restart the worker, and show the truth check passing.
- [ ] Rate a period from the dashboard, open the invoice breakdown, run replay, and explain the
      hash.
- [ ] Register a webhook pointing at the sink, trigger an event, show the signed delivery and verify
      the signature by hand (`WebhookSigner.verify`).
- [ ] Find the same ingest request in Jaeger and in Grafana.
- [ ] Run `mvn verify` and explain what each failing test would mean.

## Read it

- [ ] Walk through `MeteringService.process` line by line: in-batch dedup, insert-returning, period
      locking, folding, adjustments, rejections, flush.
- [ ] Explain every statement in `UsageEventStore.upsertBuckets` and `quantityFromLog`.
- [ ] Explain `BillingService.rateLocked` including why it needs `FOR UPDATE` and what `@Version`
      adds.
- [ ] Explain `RatingEngine.price` for each pricing model with a worked example.
- [ ] Explain `TokenBucket.tryConsume` (GCRA) and why the baseline is `max(current, now)`.
- [ ] Explain `OutboxPublisher.publishBatch` and where a duplicate publication can come from.
- [ ] Explain the Spring Modulith module graph and the one dependency inversion in it.

## Change it

- [ ] Add a new aggregation (e.g. `AVG`) end to end: enum, extractor, bucket column/expression,
      replay SQL, tests.
- [ ] Add a new pricing model (e.g. `STAIRSTEP`) with example and property tests.
- [ ] Add a new rejection reason and surface it in metrics and the dashboard.
- [ ] Write a new integration test using `TestTenant`/`ApiClient`.
- [ ] Change the acceptance-window semantics and update ADR-003 accordingly.

## Explain the numbers

- [ ] Quote the measured ingest throughput, p99 latencies and entitlement p99 from
      `docs/performance`, together with the hardware and the caveats.
- [ ] Explain which numbers would change on real servers and which would not.

## Know the limits

- [ ] State the non-guarantees (`failure-model.md` §2) from memory.
- [ ] State the three security follow-ups and why they matter for untrusted tenants.
- [ ] Say honestly which parts were built with AI assistance (the whole codebase was produced with
      an AI coding assistant under human direction), and demonstrate that you understand and can
      modify every part of it regardless.
