package com.tinyurl.service;

import com.tinyurl.dto.CreateUrlResponse;
import com.tinyurl.dto.UrlAnalyticsResponse;

import java.time.Instant;

public interface UrlService {

    CreateUrlResponse createShortUrl(String originalUrl);

    CreateUrlResponse createShortUrl(String originalUrl, Instant expiresAt);

    String resolveAndRecordRedirect(String shortCode);

    UrlAnalyticsResponse getAnalytics(String shortCode);
}
