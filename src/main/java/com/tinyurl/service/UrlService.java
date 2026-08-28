package com.tinyurl.service;

import com.tinyurl.dto.CreateUrlResponse;
import com.tinyurl.dto.UrlAnalyticsResponse;

import java.time.Instant;

public interface UrlService {

    default CreateUrlResponse createShortUrl(String originalUrl) {
        return createShortUrl(originalUrl, null);
    }

    CreateUrlResponse createShortUrl(String originalUrl, Instant expiresAt);

    default String resolveAndRecordRedirect(String shortCode) {
        return resolveAndRecordRedirect(shortCode, null, null);
    }

    String resolveAndRecordRedirect(String shortCode, String referrer, String userAgent);

    UrlAnalyticsResponse getAnalytics(String shortCode);

    void deactivate(String shortCode);
}
