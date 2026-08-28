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
