# ADR-008: No LLM / agentic components in the core system

**Status:** Accepted (2026-10-23)

## Context

The reference vacancy and 24 % of the surveyed postings mention AI-assisted or agentic development.
The brief asks whether the product genuinely benefits from an AI component and insists it must not
be added for fashion.

## Assessment

Candidate uses were evaluated against the rule "meaningful, bounded, with deterministic fallback":

| Candidate | Verdict |
|---|---|
| Natural-language invoice explanation ("why is this $137.50?") | Already answered deterministically by the per-line `breakdown` and replay; an LLM would paraphrase facts the system can render as text itself |
| Usage anomaly detection / explanation | Valuable, but the detection itself should be statistical (baseline vs. current period per meter) and deterministic; an LLM could only add prose |
| Pricing plan assistant (translate a pricing page into plan JSON) | Useful onboarding tooling, outside the runtime path; a reasonable future CLI, not a backend capability |
| Incident summarisation from metrics/logs | Operational tooling that belongs to the observability stack, not to a billing engine |

None of these change what the system guarantees, and all would make the core depend on a paid,
non-deterministic external provider — the opposite of what a metering system must be.

## Decision

Tallyard ships without LLM features. The system is **stronger** without them: every answer it gives
(counts, amounts, allow/deny, replay match) is exact, reproducible and testable.

AI-assisted *development* was used while building the project (as the reference employer expects);
that is a process choice, not a product feature, and does not change the ownership checklist in
`docs/interview/ownership-checklist.md`.

## Future option

A deterministic anomaly detector (per-meter z-score against trailing periods, emitted as
`usage.anomaly` outbox events) is the appropriate next feature in this space. If an explanation
layer is ever added, it must be an optional module behind a provider abstraction with the detector's
numeric output as the only input and a rule-based text fallback.
