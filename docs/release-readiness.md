# Release readiness and human quality gate

## Automated entry gate

A candidate is reviewable only when:

- `./mvnw --batch-mode --no-transfer-progress clean verify` passes on Java 21;
- all three orchestration scenario tests pass;
- Flyway validates both clean creation and legacy-row preservation;
- retry, safe-stop, rollback, policy-denial, and replanning tests pass;
- generated traceability contains no `NOT VERIFIED` criterion;
- the pull request contains architecture, setup, risks, limitations, and evidence.

## Human exit gate

The reviewer owns the final decision and must confirm:

- normalized intent and assumptions match stakeholder expectations;
- public API and schema changes are acceptable;
- privacy and availability boundaries are explicit;
- rollback/recovery instructions are operationally usable;
- CI is green and review findings are accepted, fixed, or recorded as trade-offs;
- the merge target is `Branch1` and no prohibited operation is requested.

Merging the reviewed pull request is the release authorization record. Agents do
not deploy, alter production data, bypass branch protection, or approve their own
output.

## Safe-stop criteria

Do not merge when tests fail, migration preservation is unproven, traceability is
incomplete, an approval is missing, or a material ambiguity has no documented
owner decision. Replan after upstream changes instead of carrying stale approval.
