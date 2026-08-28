package com.tinyurl.service.impl;

import com.tinyurl.config.TinyUrlProperties;
import com.tinyurl.domain.UrlMapping;
import com.tinyurl.domain.UrlRepository;
import com.tinyurl.dto.CreateUrlResponse;
import com.tinyurl.dto.UrlAnalyticsResponse;
import com.tinyurl.exception.InvalidUrlException;
import com.tinyurl.exception.ShortCodeGenerationException;
import com.tinyurl.exception.UrlNotFoundException;
import com.tinyurl.service.UrlService;
import com.tinyurl.util.ShortCodeGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;

@Service
public class UrlServiceImpl implements UrlService {

    private static final int MAX_SHORT_CODE_ATTEMPTS = 5;

    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlRepository repository;
    private final Clock clock;
    private final TinyUrlProperties properties;

    public UrlServiceImpl(
            ShortCodeGenerator shortCodeGenerator,
            UrlRepository repository,
            Clock clock,
            TinyUrlProperties properties) {
        this.shortCodeGenerator = shortCodeGenerator;
        this.repository = repository;
        this.clock = clock;
        this.properties = properties;
    }

    public CreateUrlResponse createShortUrl(String originalUrl) {
        validateUrl(originalUrl);

        for (int attempt = 0; attempt < MAX_SHORT_CODE_ATTEMPTS; attempt++) {
            String shortCode = shortCodeGenerator.generate();
            if (repository.existsByShortCode(shortCode)) {
                continue;
            }

            try {
                Instant createdAt = clock.instant();
                UrlMapping saved = repository.saveAndFlush(
                        new UrlMapping(shortCode, originalUrl, createdAt));
                return toCreateResponse(saved);
            } catch (DataIntegrityViolationException exception) {
                // A concurrent request may have persisted the same generated code
                // after the existence check. Generate another candidate.
            }
        }

        throw new ShortCodeGenerationException();
    }

    @Transactional
    public String resolveAndRecordRedirect(String shortCode) {
        UrlMapping mapping = findByShortCode(shortCode);
        repository.recordRedirect(shortCode, clock.instant());
        return mapping.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getAnalytics(String shortCode) {
        UrlMapping mapping = findByShortCode(shortCode);
        return new UrlAnalyticsResponse(
                mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getRedirectCount(),
                mapping.getCreatedAt(),
                mapping.getLastAccessedAt());
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
                mapping.getCreatedAt());
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
