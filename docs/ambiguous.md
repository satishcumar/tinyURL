# TinyURL Ambiguous Requirements

## Purpose

This document captures requirements whose intent is understandable but whose implementation boundaries and acceptance criteria are incomplete. Each item must be clarified and normalized before task decomposition, architecture, implementation, and validation begin.

| Ambiguous Requirement | Missing Decisions |
|---|---|
| “Make TinyURL production-ready” | Expected traffic, deployment platform, database, availability, security, and recovery targets |
| “Add better analytics” | Required metrics, retention, accuracy, privacy restrictions, and reporting format |
| “Make redirects faster” | Current latency, target latency, percentile, expected load, and caching consistency |
| “Prevent malicious URLs” | Definition of malicious, DNS/private-address policy, blocklists, and false-positive handling |
| “Support custom URLs” | Allowed characters, case sensitivity, reserved words, ownership, and conflict behavior |
| “Delete expired links” | Hard or soft deletion, retention period, analytics preservation, and scheduled cleanup |
| “Avoid duplicate URLs” | Whether duplicates are global or user-specific and whether expiration/options affect identity |
| “Add enterprise security” | Authentication model, roles, audit requirements, secrets management, and compliance scope |

## Well-defined Requirements

These requirements have measurable behavior and clear acceptance criteria.

| Requirement | Example Acceptance Criteria |
|---|---|
| URL expiration | Accept optional `expiresAt`; expired URLs return `410 Gone`; non-expired URLs redirect normally |
| Custom short codes | Accept an optional alias of 4–12 alphanumeric characters; duplicate aliases return `409 Conflict` |
| Database migration | Flyway creates the `url_mapping` table; Hibernate uses `ddl-auto: validate`; existing records survive restart |
| Rate limiting | Allow 20 URL-creation requests per client per minute; exceeding the limit returns `429` |
| URL deactivation | A deactivated URL returns `410 Gone`; analytics remain available |
| Actuator health test | `/actuator/health` returns `200` and `UP` when the application and database are healthy |
