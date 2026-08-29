# TinyURL agentic SDLC assessment package

## Outcome

The prototype transforms greenfield, brownfield, and ambiguous requirements into
typed analysis, dependency-aware plans, governed execution, production code,
tests, documentation, and review evidence. Agents operate inside policy and retry
boundaries; humans approve plans, schema changes, and the final pull-request merge.

## Deliverable map

| Assessment expectation | Evidence |
|---|---|
| Working prototype | Spring Boot API, H2/Flyway persistence, orchestration API |
| Requirement understanding | Scenario-specific criteria, assumptions, ambiguities, risks, repository impact |
| Task decomposition | Validated DAGs with parallel paths and synchronization |
| Controlled autonomy | Policy engine, plan/schema approval gates, prohibited actions |
| Failure recovery | Bounded retries, safe-stop, reverse rollback records, Flyway runbook |
| Dynamic replanning | Requirement versions and transitive dependency invalidation |
| Traceability | Acceptance-criterion matrix and append-only decision/command evidence |
| Reliability | Success rate, retries, rollbacks, safe stops, MTTR, end-to-end latency |
| Release readiness | CI entry gate and human PR merge exit gate |

## Demonstration

1. Run `bash mvnw clean verify`.
2. Start the service with `bash mvnw spring-boot:run`.
3. In another terminal run `./scripts/demo-all-scenarios.sh`.
4. Review each printed state transition and the generated directories under
   `build/orchestration-runs/{executionId}`.
5. Show a replan by changing an upstream task and confirm approvals are cleared.
6. Show the CI result and use PR review/merge as final human authorization.

## Key engineering decisions

- Flyway owns schema evolution; Hibernate validates rather than mutates schema.
- Analytics uses existing aggregate data and avoids visitor-level collection.
- Approval is required before executing a plan; database DDL requires a second,
  scoped approval.
- Task execution may be parallel, but graph dependencies determine eligibility.
- Transient failures retry within a fixed budget; other failures safe-stop.
- Raw command output is not persisted; command metadata stores an output digest.

## Risks and trade-offs

- Filesystem state is suitable for a demonstrator, not horizontally scaled use.
- Caller-asserted approver identity requires authentication and authorization in
  production.
- Execution is synchronous and task adapters are deterministic rather than an
  external LLM/coding-agent integration.
- Aggregate redirect rate is intentionally simple; richer dimensions require a
  privacy, retention, and availability design.
- H2 is appropriate for the prototype; production deployment should use a
  managed database with tested backup and restore automation.

## Final limitations

The prototype demonstrates orchestration mechanics and engineering ownership,
not unattended production deployment. Production hardening requires durable
transactional workflow storage, authenticated role-based approvals, distributed
locking, secret management, telemetry export, signed artifacts, and protected
environment deployment controls.

See [architecture](architecture-overview.md), [release readiness](release-readiness.md),
and [orchestration details](agentic-orchestration.md).
