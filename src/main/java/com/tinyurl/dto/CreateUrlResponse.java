package com.tinyurl.dto;

import java.time.Instant;

public record CreateUrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        String status
) {
    public CreateUrlResponse(String shortCode, String shortUrl, String originalUrl, Instant createdAt) {
        this(shortCode, shortUrl, originalUrl, createdAt, null, "ACTIVE");
    }
}
