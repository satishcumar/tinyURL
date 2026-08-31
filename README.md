# tinyURL

Spring Boot URL shortener with expiration, analytics, reliability tests, and a
stateful agentic SDLC orchestration prototype.

## Run

For prerequisites, clean setup, local verification, Windows/Python guidance,
and the full three-scenario demonstration, see the [setup guide](docs/setup.md).

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

The greenfield scenario is defined in
[URL Expiration](orchestration/requirements/url-expiration.md)

The brownfield scenario is defined in
[Flyway schema ownership](orchestration/requirements/flyway-migration.md), with
operational controls in the [migration and recovery runbook](docs/flyway-recovery.md).

The ambiguous scenario is documented in
[Privacy-preserving richer analytics](orchestration/requirements/richer-analytics.md).

For the complete assessment walkthrough, architecture, release gates, evidence,
and final limitations, see the [final assessment package](docs/final-assessment.md).
