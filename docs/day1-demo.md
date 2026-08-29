# Day 1 assessment demonstration

## What the demonstration proves

One command sequence transforms an expiration requirement into normalized
acceptance criteria, a dependency graph, a human-approved execution, validation
metrics, and a review package. Governance remains in deterministic application
code rather than inside the agent prompt.

## Setup

Requirements:

- Java 21
- Bash
- Python 3
- curl

Validate and start the service:

```bash
bash mvnw clean verify
bash mvnw spring-boot:run
```

In a second terminal, run:

```bash
./scripts/demo-day1-orchestration.sh
```

The script prints each state transition and the generated artifact names. The
review package is written to:

```text
build/orchestration-runs/{executionId}/
```

## Expected control flow

1. Requirement intake returns `AWAITING_PLAN_APPROVAL`.
2. The plan exposes five acceptance criteria and a five-node dependency graph.
3. Command metadata is recorded using an output digest rather than raw output.
4. Human approval moves the workflow to `READY_FOR_EXECUTION`.
5. Implementation and test design become parallel-ready after design.
6. Validation runs only after both branches succeed.
7. The final state is `COMPLETED` with a success rate of `1.0`.
8. Metrics, traceability, and engineering-summary artifacts are generated.

## Failure demonstration

Automated engine tests also prove:

- transient failure succeeds after bounded retry;
- a permanent failure produces `SAFE_STOPPED`;
- downstream tasks become `BLOCKED`;
- reversible completed tasks receive compensating rollback;
- prohibited actions remain denied after plan approval.

Run the focused tests with:

```bash
bash mvnw -Dtest=WorkflowExecutionEngineTest,OrchestrationEndToEndTest test
```

## Known limitations

- Requirement analysis is deterministic and specialized for URL expiration.
- Workflow execution is synchronous within one application instance.
- The latest state is memory-cached and filesystem-persisted, not transactional.
- Approval identity is caller-supplied; production use requires authentication
  and authorization.
- Dynamic replanning and decision-lineage invalidation are planned for Day 2.
- Runtime task runners demonstrate governed execution but do not yet invoke an
  external coding-agent provider.

See `docs/examples/day1-run` for a stable sample review package.
