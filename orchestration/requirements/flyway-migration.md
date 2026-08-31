# Brownfield: Flyway schema ownership

Replace Hibernate `create-drop` schema management with Flyway migrations while
preserving existing H2 file data.

## Acceptance criteria

- AC-1: Flyway creates the current schema for a clean database.
- AC-2: An existing matching schema is baselined without losing rows.
- AC-3: Hibernate uses `ddl-auto: validate` and never creates or drops tables.
- AC-4: Migration or validation failure prevents application readiness.
- AC-5: A recovery point and recovery procedure are recorded before schema change.

## Governance

Plan approval does not authorize DDL. The workflow must enter
`AWAITING_SCHEMA_APPROVAL`; a database owner must approve the recovery point and
migration before execution becomes ready.
