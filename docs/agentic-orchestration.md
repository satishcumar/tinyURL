# Agentic SDLC orchestration

## Architecture

The orchestration layer is a deterministic control plane around specialized
engineering agents. Agents may analyze requirements, propose tasks, edit code,
and generate tests; the workflow engine owns state transitions, dependency
checks, policy decisions, approvals, and evidence persistence.

The initial vertical slice implements:

1. requirement intake and normalization;
2. acceptance criteria, assumptions, ambiguity, and risk extraction;
3. a validated directed acyclic task graph;
4. policy classification of automatic, approval-required, and prohibited actions;
5. a mandatory plan approval gate;
6. durable workflow snapshots, append-only event records, and command records.

## State model

`RECEIVED`, `ANALYZING`, and `PLANNING` are transient orchestration states.
The first externally observable safe stop is `AWAITING_PLAN_APPROVAL`. Approval
moves the workflow to `READY_FOR_EXECUTION`. Later stages use `RUNNING`,
`VALIDATING`, `COMPLETED`, `BLOCKED`, and `SAFE_STOPPED`.

Only the workflow service may transition state. Duplicate or out-of-order
approval returns `409 Conflict`.

## Dependency graph

The URL-expiration scenario produces this graph:

```text
inspect -> design -> implement -----> validate
                  \-> test-design -->/
```

`implement` and `test-design` are parallel-ready after `design`. `validate` is
a synchronization node and cannot run until both predecessors succeed. The
graph validator rejects duplicate identifiers, missing dependencies, and cycles.

## API

Create and plan a workflow:

```http
POST /api/v1/workflows
Content-Type: application/json

{"requirement":"Add URL expiration and lifecycle management"}
```

Approve the generated plan:

```http
POST /api/v1/workflows/{id}/plan-approval
Content-Type: application/json

{"approvedBy":"satish","rationale":"Scope and risks reviewed"}
```

Inspect state and evidence:

```http
GET /api/v1/workflows/{id}
GET /api/v1/workflows/{id}/artifacts
```

An execution adapter records a command without storing its full output, which
could contain credentials or personal data:

```http
POST /api/v1/workflows/{id}/commands
Content-Type: application/json

{
  "stageId":"validate",
  "command":"bash mvnw test",
  "exitCode":0,
  "startedAt":"2026-08-29T00:00:00Z",
  "durationMillis":1200,
  "outputDigest":"sha256:..."
}
```

## Evidence and recovery

Each run is written beneath `ORCHESTRATION_ARTIFACT_ROOT`:

- `workflow.json`: atomic latest-state snapshot;
- `events.jsonl`: append-only decisions and state events;
- `commands.jsonl`: append-only command metadata and output digests.

Workflow identifiers must be UUIDs, and resolved artifact paths are constrained
to the configured root. A workflow absent from memory is restored from its
snapshot, allowing approval to resume after application restart.

## Current limitations

- Requirement analysis is a deterministic URL-expiration implementation; an LLM
  adapter will replace it while retaining the same typed output contract.
- Task execution, bounded retries, rollback, dynamic replanning, and reliability
  metrics are the next orchestration increment.
- The prototype uses filesystem persistence. A transactional database and
  authenticated approver identity are required for multi-instance production use.
