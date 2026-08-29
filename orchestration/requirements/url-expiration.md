# URL expiration and lifecycle management

Add optional expiration to shortened URLs. A creation request may provide an
`expiresAt` timestamp. Active and non-expiring links redirect normally, expired
links return `410 Gone`, and unknown links return `404 Not Found`.

## Acceptance criteria

- AC-1: Creation accepts an optional UTC ISO-8601 `expiresAt` value.
- AC-2: A non-expired URL redirects normally.
- AC-3: An expired URL returns `410 Gone`.
- AC-4: A nonexistent short code returns `404 Not Found`.
- AC-5: Existing links without expiration continue to work.

## Approval decision

The plan must be reviewed before execution because the scenario changes the
public creation contract. Implementation, tests, and documentation may proceed
automatically after that approval within the configured change limits.
