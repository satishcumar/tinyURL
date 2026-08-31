# Ambiguous scenario: richer analytics

## Original request

“Provide richer analytics.”

## Clarified engineering contract

The prototype interprets richer analytics as useful aggregate insights derived
from data already stored: link age and average redirects per active day. The
existing response fields remain compatible. The plan-approval gate is the human
checkpoint that accepts or rejects this interpretation before execution.

## Privacy boundary

- Do not collect or return IP addresses, user agents, referrers, location, or a
  visitor identifier.
- Do not add per-request analytics event storage.
- Report `dataScope=AGGREGATE_ONLY` so the contract is explicit and reviewable.
- Any future visitor-level dimension requires a new requirement, privacy review,
  retention decision, threat model, and explicit approval.

## Availability boundary

Redirect processing continues to update only the existing aggregate counter and
timestamp. The richer analytics calculations run only on the analytics read
endpoint; they cannot block or fail the redirect path.

## Derived metric definitions

- `ageSeconds`: non-negative UTC seconds from creation to the observation time.
- `averageRedirectsPerDay`: redirect count divided by elapsed 24-hour days, with
  a one-day minimum denominator to avoid unstable rates for new links.

## Validation

- Unit tests cover mature and newly created links.
- Controller tests confirm prohibited visitor fields are absent.
- The orchestration integration test verifies ambiguity capture, the approval
  stop, the seven-node dependency graph, and full traceability execution.
