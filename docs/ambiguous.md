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
