# TinyURL

A Spring Boot URL-shortening service with expiring links, soft deactivation,
concurrency-safe redirect counts, and privacy-safe analytics.

## API

- `POST /api/v1/urls` creates a short URL. The JSON body accepts `url` and an
  optional ISO-8601 `expiresAt` value.
- `GET /{shortCode}` redirects active, unexpired links and returns `410 Gone`
  for expired or deactivated links.
- `DELETE /api/v1/urls/{shortCode}` soft-deactivates a link while retaining its
  analytics.
- `GET /api/v1/urls/{shortCode}/analytics` returns lifetime totals plus daily
  and coarse client-category counts for the last 30 days.

Analytics stores only the redirect timestamp, referrer host, and a coarse
client category. IP addresses and full user-agent strings are not persisted.
Analytics-event failures are isolated so they do not prevent valid redirects.

## Database lifecycle

Flyway owns the database schema. The local profile uses file-based H2 and
Hibernate `ddl-auto: validate`; existing Hibernate-created databases are
baselined before the lifecycle migration is applied. New databases run all
migrations from `src/main/resources/db/migration`.

Run the application with `./mvnw spring-boot:run` and run the verification suite
with `./mvnw clean verify`.
