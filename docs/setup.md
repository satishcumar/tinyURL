# TinyURL setup and local demonstration

This guide sets up the `Branch1` assessment prototype, runs its verification
suite, starts the service, and generates reviewable evidence for all three
agentic SDLC scenarios.

## Prerequisites

- Git
- Java 21 (a JDK, not only a JRE)
- Bash (`Git Bash` on Windows, or a Linux/macOS shell)
- `curl`
- Python 3: the demo script currently uses the Windows Python launcher `py`

Maven does not need to be installed globally because the repository includes
the Maven wrapper.

Verify the tools before continuing:

```bash
git --version
java -version
bash --version
curl --version
py --version
```

On Linux or macOS, `python3 --version` normally replaces `py --version`; see
[Python launcher compatibility](#python-launcher-compatibility).

## Clone and select the assessment branch

```bash
git clone https://github.com/satishcumar/tinyURL.git
cd tinyURL
git switch Branch1
```

If the repository is already cloned:

```bash
git fetch origin
git switch Branch1
git pull --ff-only origin Branch1
```

## Build and test

From the repository root, run the complete verification lifecycle:

```bash
bash mvnw --batch-mode --no-transfer-progress clean verify
```

The first run downloads Maven dependencies and therefore requires internet
access. A successful run completes compilation, unit tests, integration tests,
and packaging without failures.

## Start the application

```bash
bash mvnw spring-boot:run
```

The application listens on `http://localhost:8080`. The default `local` profile
uses Flyway-managed H2 storage at `./data/tinyurl`. Hibernate validates the
schema and does not create or update it.

Keep this terminal running. In a second terminal, confirm that the service is
ready:

```bash
curl --fail http://localhost:8080/actuator/health
```

The response should contain `"status":"UP"`.

## Basic URL-shortener check

Create a short URL:

```bash
curl --fail --silent --show-error \
  -X POST http://localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com"}'
```

Copy the returned `shortCode` and use it in the following commands:

```bash
curl --include http://localhost:8080/<shortCode>
curl --fail http://localhost:8080/api/v1/urls/<shortCode>/analytics
```

The first request should return HTTP `302` with a `Location` header. The second
returns aggregate analytics for the short URL.

## Run the assessment scenarios

With the application still running, execute this from a second terminal:

```bash
bash scripts/demo-all-scenarios.sh http://localhost:8080
```

The script demonstrates:

1. Greenfield URL-expiration planning and execution.
2. Brownfield Flyway migration with plan and schema approval gates.
3. Ambiguous richer-analytics clarification and governed execution.

Every scenario should finish with `COMPLETED`. The output prints an execution
ID and its artifact directory.

## Review generated evidence

Each run is retained under:

```text
build/orchestration-runs/<executionId>/
```

Review the workflow snapshot, append-only events, decision and command records,
traceability matrix, metrics, and engineering summary. Retain these directories
for the presentation because they demonstrate requirement lineage, approvals,
execution, validation, retries or rollback records, and release evidence.

The H2 database under `data/` persists between local restarts. Do not delete it
or the generated run directories until the evidence has been reviewed or
copied to the required assessment location.

## Python launcher compatibility

The committed demo script uses `py`, which matches Python installations made
through the Windows launcher. If Git Bash reports that Python was not found,
verify:

```bash
py --version
```

If `py` works, rerun the script from the same Git Bash terminal. If Windows
opens the Microsoft Store instead, disable the `python.exe` and `python3.exe`
App execution aliases in Windows Settings and confirm that the Python launcher
is on `PATH`.

On Linux or macOS, where `python3` exists but `py` does not, use a temporary
shell function without modifying the repository:

```bash
py() { python3 "$@"; }
export -f py
bash scripts/demo-all-scenarios.sh http://localhost:8080
```

## Configuration overrides

The most useful environment variables are:

| Variable | Default | Purpose |
|---|---|---|
| `TINYURL_BASE_URL` | `http://localhost:8080` | Base URL returned for shortened links |
| `ORCHESTRATION_ARTIFACT_ROOT` | `build/orchestration-runs` | Generated evidence location |
| `ORCHESTRATION_MAX_ATTEMPTS` | `3` | Bounded task-attempt limit |
| `ORCHESTRATION_PARALLELISM` | `3` | Maximum orchestration parallelism |

Set overrides before starting the application. For example:

```bash
export ORCHESTRATION_ARTIFACT_ROOT=build/demo-evidence
bash mvnw spring-boot:run
```

## Troubleshooting

| Symptom | Resolution |
|---|---|
| Java version is below 21 | Install JDK 21 and update `JAVA_HOME` and `PATH` |
| Port 8080 is already in use | Stop the conflicting process before starting TinyURL |
| Health check fails | Wait for `Started TinyurlApplication`, then retry the health URL |
| `py: command not found` on Linux/macOS | Define the temporary `py()` function shown above |
| Flyway validation fails | Preserve the database, inspect the migration error, and follow `docs/flyway-recovery.md` rather than editing an applied migration |
| Demo stops before completion | Inspect the application log and the execution directory; the workflow records its safe-stop or failure state |

Stop the application with `Ctrl+C` after the demo finishes.
