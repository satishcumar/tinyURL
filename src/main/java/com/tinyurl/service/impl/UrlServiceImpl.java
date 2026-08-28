package com.tinyurl.service.impl;

import com.tinyurl.config.TinyUrlProperties;
import com.tinyurl.domain.UrlMapping;
import com.tinyurl.domain.UrlRepository;
import com.tinyurl.domain.RedirectEvent;
import com.tinyurl.domain.RedirectEventRepository;
import com.tinyurl.dto.CreateUrlResponse;
import com.tinyurl.dto.DailyRedirectCount;
import com.tinyurl.dto.UrlAnalyticsResponse;
import com.tinyurl.exception.InvalidUrlException;
import com.tinyurl.exception.ShortCodeGenerationException;
import com.tinyurl.exception.UrlNotFoundException;
import com.tinyurl.exception.UrlUnavailableException;
import com.tinyurl.service.RedirectAnalyticsRecorder;
import com.tinyurl.service.UrlService;
import com.tinyurl.util.ShortCodeGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class UrlServiceImpl implements UrlService {

    private static final int MAX_SHORT_CODE_ATTEMPTS = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlServiceImpl.class);

    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlRepository repository;
    private final Clock clock;
    private final TinyUrlProperties properties;
    private final RedirectAnalyticsRecorder analyticsRecorder;
    private final RedirectEventRepository redirectEventRepository;

    public UrlServiceImpl(
            ShortCodeGenerator shortCodeGenerator,
            UrlRepository repository,
            Clock clock,
            TinyUrlProperties properties,
            RedirectAnalyticsRecorder analyticsRecorder,
            RedirectEventRepository redirectEventRepository) {
        this.shortCodeGenerator = shortCodeGenerator;
        this.repository = repository;
        this.clock = clock;
        this.properties = properties;
        this.analyticsRecorder = analyticsRecorder;
        this.redirectEventRepository = redirectEventRepository;
    }

    public CreateUrlResponse createShortUrl(String originalUrl, Instant expiresAt) {
        validateUrl(originalUrl);
        validateExpiration(expiresAt);

        for (int attempt = 0; attempt < MAX_SHORT_CODE_ATTEMPTS; attempt++) {
            String shortCode = shortCodeGenerator.generate();
            if (repository.existsByShortCode(shortCode)) {
                continue;
            }

            try {
                Instant createdAt = clock.instant();
                UrlMapping saved = repository.saveAndFlush(
                        new UrlMapping(shortCode, originalUrl, createdAt, expiresAt));
                return toCreateResponse(saved);
            } catch (DataIntegrityViolationException exception) {
                // A concurrent request may have persisted the same generated code
                // after the existence check. Generate another candidate.
            }
        }

        throw new ShortCodeGenerationException();
    }

    @Transactional
    public String resolveAndRecordRedirect(String shortCode, String referrer, String userAgent) {
        UrlMapping mapping = findByShortCode(shortCode);
        Instant now = clock.instant();
        if (!mapping.isActive() || mapping.isExpired(now)) {
            throw new UrlUnavailableException(shortCode);
        }
        repository.recordRedirect(shortCode, now);
        try {
            analyticsRecorder.record(shortCode, now, referrer, userAgent);
        } catch (RuntimeException exception) {
            LOGGER.warn("Redirect analytics recording failed for short code {}", shortCode, exception);
        }
        return mapping.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getAnalytics(String shortCode) {
        UrlMapping mapping = findByShortCode(shortCode);
        Instant now = clock.instant();
        List<RedirectEvent> events = redirectEventRepository
                .findByShortCodeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
                        shortCode,
                        now.minus(30, ChronoUnit.DAYS));
        Map<LocalDate, Long> dailyCounts = new TreeMap<>();
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        Map<String, Long> referrerCounts = new LinkedHashMap<>();
        for (RedirectEvent event : events) {
            LocalDate date = event.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
            dailyCounts.merge(date, 1L, Long::sum);
            categoryCounts.merge(event.getClientCategory(), 1L, Long::sum);
            String referrerHost = event.getReferrerHost() == null ? "DIRECT" : event.getReferrerHost();
            referrerCounts.merge(referrerHost, 1L, Long::sum);
        }
        return new UrlAnalyticsResponse(
                mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getRedirectCount(),
                mapping.getCreatedAt(),
                mapping.getLastAccessedAt(),
                mapping.getExpiresAt(),
                statusOf(mapping, now),
                dailyCounts.entrySet().stream()
                        .map(entry -> new DailyRedirectCount(entry.getKey(), entry.getValue()))
                        .toList(),
                Map.copyOf(categoryCounts),
                Map.copyOf(referrerCounts));
    }

    @Transactional
    public void deactivate(String shortCode) {
        UrlMapping mapping = findByShortCode(shortCode);
        if (mapping.isActive()) {
            mapping.deactivate(clock.instant());
        }
    }

    private UrlMapping findByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
    }

    private CreateUrlResponse toCreateResponse(UrlMapping mapping) {
        return new CreateUrlResponse(
                mapping.getShortCode(),
                properties.getBaseUrl() + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                statusOf(mapping, clock.instant()));
    }

    private void validateExpiration(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            throw new InvalidUrlException("Expiration must be in the future");
        }
    }

    private String statusOf(UrlMapping mapping, Instant now) {
        if (!mapping.isActive()) {
            return "INACTIVE";
        }
        if (mapping.isExpired(now)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    private void validateUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidUrlException("URL is required");
        }

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null ||
                    !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new InvalidUrlException("Only valid HTTP/HTTPS URLs are supported");
            }
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("Invalid URL format");
        }
    }
}
