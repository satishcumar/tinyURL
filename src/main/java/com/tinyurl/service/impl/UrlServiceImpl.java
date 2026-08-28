package com.tinyurl.service.impl;

import com.tinyurl.domain.UrlMapping;
import com.tinyurl.domain.UrlRepository;
import com.tinyurl.dto.CreateUrlResponse;
import com.tinyurl.dto.UrlAnalyticsResponse;
import com.tinyurl.exception.InvalidUrlException;
import com.tinyurl.exception.UrlNotFoundException;
import com.tinyurl.service.UrlService;
import com.tinyurl.util.ShortCodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;

@Service
public class UrlServiceImpl implements UrlService {

    private static final int MAX_SHORT_CODE_ATTEMPTS = 5;
    @Autowired
    private ShortCodeGenerator shortCodeGenerator;
    private final Clock clock = Clock.systemUTC();
    @Autowired
    UrlRepository repository;
    @Transactional
    public CreateUrlResponse createShortUrl(String originalUrl) {
        validateUrl(originalUrl);

        String shortCode = generateUniqueShortCode();
        Instant createdAt = clock.instant();
        UrlMapping saved = repository.save(new UrlMapping(shortCode, originalUrl, createdAt));

        String baseUrl = "http://localhost:8080";
        return new CreateUrlResponse(
                saved.getShortCode(),
                baseUrl + "/" + saved.getShortCode(),
                saved.getOriginalUrl(),
                saved.getCreatedAt());
    }

    @Transactional
    public String resolveAndRecordRedirect(String shortCode) {
        UrlMapping mapping = findByShortCode(shortCode);
        mapping.recordRedirect(clock.instant());
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

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < MAX_SHORT_CODE_ATTEMPTS; attempt++) {
            String candidate = shortCodeGenerator.generate();
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique short code");
    }

    private void validateUrl(String value) {
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
