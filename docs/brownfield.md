# TinyURL Brownfield Refactoring Backlog

## Purpose

This document records the prioritized brownfield improvements identified during review of the existing TinyURL application. These items cover correctness, concurrency, configuration isolation, testability, maintainability, and code quality.

| Priority | Refactor | Reason |
|---|---|---|
| High | Make redirect counting concurrency-safe | Concurrent redirects can lose count updates |
| High | Handle short-code insert races | `existsByShortCode()` followed by `save()` is not atomic |
| High | Separate local/test configuration | Tests currently inherit the file-based H2 configuration |
| Medium | Replace field injection with constructor injection | Makes dependencies explicit and tests simpler |
| Medium | Externalize the public base URL | `http://localhost:8080` is hard-coded |
| Medium | Inject `Clock` | Enables deterministic timestamp tests |
| Medium | Move the global exception handler into its own class | An advice class nested inside an exception is difficult to maintain |
| Medium | Improve unmapped exception handling | Retry exhaustion and database conflicts currently lack controlled responses |
| Low | Remove redundant or unused dependencies and methods | Reduces build complexity and confusion |
| Low | Improve naming and formatting | Makes the code more consistent and reviewable |

## Test Case Enhancement Backlog

The following test enhancements strengthen regression protection for the brownfield refactoring work and validate application behavior across API, service, persistence, concurrency, configuration, and operational boundaries.

| Priority | Enhancement | Why |
|---|---|---|
| High | End-to-end API test | Proves controller, service, repository, transaction, and H2 work together |
| High | Real concurrent redirect test | Current repository test increments sequentially, not concurrently |
| High | Database uniqueness test | Verifies the `short_code` constraint directly |
| High | Insert-race retry exhaustion test | Confirms repeated database conflicts stop after five attempts |
| Medium | URL validation boundary tests | Covers null, blank, length, scheme, host, and malformed URLs |
| Medium | Exception-handler tests | Covers 400, 404, 503, and generic 500 responses |
| Medium | Configuration integration test | Confirms test profile uses memory H2 and the configured base URL |
| Low | Actuator health test | Confirms operational endpoints remain available |

## Application Enhancement Backlog

These enhancements extend the existing application beyond corrective refactoring. They improve data durability, security, URL lifecycle management, scalability, API usability, observability, and reporting.

| Priority | Enhancement | Value |
|---|---|---|
| Critical | Replace `ddl-auto: create-drop` | The local file-based H2 database is erased whenever the application restarts |
| High | Add Flyway database migrations | Provides controlled, versioned schema changes |
| High | Add URL expiration | Supports temporary links and prevents indefinite data growth |
| High | Add rate limiting | Protects URL creation and redirect endpoints from abuse |
| High | Add custom short codes | Allows users to request memorable aliases such as `/spring-guide` |
| High | Validate unsafe destinations | Rejects dangerous schemes and optionally blocks localhost/private-network destinations |
| Medium | Add URL deactivation/deletion | Enables lifecycle management without immediately deleting analytics |
| Medium | Add duplicate URL handling | Optionally returns an existing short URL instead of creating duplicates |
| Medium | Add caching for redirects | Reduces database reads for frequently accessed links |
| Medium | Add richer analytics | Tracks daily redirects, referrers, browsers, and approximate geography |
| Medium | Add OpenAPI/Swagger documentation | Makes the REST API discoverable and easier to test |
| Medium | Add structured logging and correlation IDs | Improves troubleshooting and audit traceability |
| Low | Add QR-code generation | Provides a useful consumer-facing feature |
| Low | Add analytics pagination/export | Supports reporting when usage grows |
