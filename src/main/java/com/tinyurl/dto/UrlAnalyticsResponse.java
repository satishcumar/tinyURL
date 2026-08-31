package com.tinyurl.dto;
import java.time.Instant;

public record UrlAnalyticsResponse(
        String shortCode,
        String originalUrl,
        long redirectCount,
        Instant createdAt,
        Instant lastAccessedAt,
        Instant expiresAt,
        String status,
        long ageSeconds,
        double averageRedirectsPerDay,
        String dataScope
) {
    public UrlAnalyticsResponse(
            String shortCode,
            String originalUrl,
            long redirectCount,
            Instant createdAt,
            Instant lastAccessedAt,
            Instant expiresAt,
            String status) {
        this(shortCode, originalUrl, redirectCount, createdAt, lastAccessedAt, expiresAt, status,
                0, 0.0, "AGGREGATE_ONLY");
    }

    public UrlAnalyticsResponse(
            String shortCode,
            String originalUrl,
            long redirectCount,
            Instant createdAt,
            Instant lastAccessedAt) {
        this(shortCode, originalUrl, redirectCount, createdAt, lastAccessedAt, null, "ACTIVE",
                0, 0.0, "AGGREGATE_ONLY");
    }
}
