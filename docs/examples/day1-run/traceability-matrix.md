# Acceptance-criteria traceability

| Criterion | Requirement | Tasks | Evidence status |
|---|---|---|---|
| AC-1 | Creation accepts an optional UTC ISO-8601 `expiresAt` value | inspect, design, implement, test-design, validate | PASS |
| AC-2 | A non-expired URL redirects normally | implement, test-design, validate | PASS |
| AC-3 | An expired URL returns `410 Gone` | design, implement, test-design, validate | PASS |
| AC-4 | A nonexistent short code returns `404 Not Found` | design, test-design, validate | PASS |
| AC-5 | Existing links without expiration continue to work | inspect, implement, test-design, validate | PASS |
