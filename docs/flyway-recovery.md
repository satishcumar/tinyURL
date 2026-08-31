# Flyway migration and recovery runbook

## Repository impact

| Component | Change | Primary risk |
|---|---|---|
| `pom.xml` | Add Flyway | Runtime dependency compatibility |
| Local/test configuration | Replace `create-drop` with `validate` | Startup failure on schema drift |
| `db/migration` | Add immutable V1 schema | Incorrect DDL or constraint mismatch |
| Existing H2 file | Baseline at version 1 | Incorrect baseline could skip required DDL |
| JPA entity | Validate against migrated schema | ORM/schema mismatch |

## First deployment

1. Stop writers or enter a maintenance window.
2. Copy `./data/tinyurl.mv.db` to an access-controlled, timestamped backup.
3. Verify the backup is non-empty and can be opened using the same H2 version.
4. Record backup location, checksum, approver, and retention expiry.
5. Start the application with Flyway enabled.
6. Confirm `flyway_schema_history` contains a successful baseline or V1 migration.
7. Run create, redirect, analytics, and legacy-row read checks.
8. Release traffic only after health and validation checks succeed.

## Recovery

If migration or Hibernate validation fails:

1. Keep the application out of service; do not bypass validation.
2. Preserve the failed database and migration logs for diagnosis.
3. Stop every process connected to the H2 file.
4. Move the failed database file aside; do not overwrite the only evidence.
5. Restore the verified backup to the configured database location.
6. Start the previous application version and verify legacy-row reads.
7. Create a corrective forward migration; never edit an applied migration.

For an already-committed production migration, recovery is backup restoration or
an approved corrective migration. Destructive `undo` SQL is not automated.
