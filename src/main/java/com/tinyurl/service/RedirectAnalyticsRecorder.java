package com.tinyurl.service;

import com.tinyurl.domain.RedirectEvent;
import com.tinyurl.domain.RedirectEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Locale;

@Service
public class RedirectAnalyticsRecorder {

    private final RedirectEventRepository repository;

    public RedirectAnalyticsRecorder(RedirectEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String shortCode, Instant occurredAt, String referrer, String userAgent) {
        repository.save(new RedirectEvent(
                shortCode,
                occurredAt,
                extractReferrerHost(referrer),
                categorizeClient(userAgent)));
    }

    private String extractReferrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        try {
            return new URI(referrer).getHost();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private String categorizeClient(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = userAgent.toLowerCase(Locale.ROOT);
        if (normalized.contains("bot") || normalized.contains("crawler") || normalized.contains("spider")) {
            return "BOT";
        }
        if (normalized.contains("mobile") || normalized.contains("android") || normalized.contains("iphone")) {
            return "MOBILE";
        }
        return "DESKTOP";
    }
}
