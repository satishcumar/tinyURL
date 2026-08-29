# tinyURL

Spring Boot URL shortener with expiration, analytics, reliability tests, and a
stateful agentic SDLC orchestration prototype.

## Run

```bash
bash mvnw spring-boot:run
```

The default `local` profile uses an H2 file database. Run tests with:

```bash
bash mvnw test
```

## Agentic workflow

The orchestration API converts a requirement into normalized acceptance
criteria and an explicit dependency graph, then stops at a human plan-approval
gate. It enforces action policies and persists workflow, event, and command
evidence. Approved workflows execute through a dependency-aware scheduler with
parallel paths, bounded transient retries, compensating rollback, safe stops,
and reliability metrics.

See [Agentic SDLC orchestration](docs/agentic-orchestration.md) and the
[URL-expiration scenario](orchestration/requirements/url-expiration.md).

For the assessment walkthrough, use the [Day 1 demonstration](docs/day1-demo.md)
and compare the output with the [sample review package](docs/examples/day1-run).

The Day 2 brownfield scenario is defined in
[Flyway schema ownership](orchestration/requirements/flyway-migration.md), with
operational controls in the [migration and recovery runbook](docs/flyway-recovery.md).

The ambiguous scenario is documented in
[Privacy-preserving richer analytics](orchestration/requirements/richer-analytics.md).
