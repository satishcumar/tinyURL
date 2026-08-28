package com.tinyurl.dto;
import java.time.Instant;

public record UrlAnalyticsResponse(
        String shortCode,
        String originalUrl,
        long redirectCount,
        Instant createdAt,
        Instant lastAccessedAt,
        Instant expiresAt,
        String status
) {
    public UrlAnalyticsResponse(
            String shortCode,
            String originalUrl,
            long redirectCount,
            Instant createdAt,
            Instant lastAccessedAt) {
        this(shortCode, originalUrl, redirectCount, createdAt, lastAccessedAt, null, "ACTIVE");
    }
}
