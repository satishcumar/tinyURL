package com.tinyurl.dto;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UrlAnalyticsResponse(
        String shortCode,
        String originalUrl,
        long redirectCount,
        Instant createdAt,
        Instant lastAccessedAt,
        Instant expiresAt,
        String status,
        List<DailyRedirectCount> dailyRedirects,
        Map<String, Long> clientCategories,
        Map<String, Long> referrerHosts
) {
}
