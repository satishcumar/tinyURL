package com.tinyurl.service.impl;

import com.tinyurl.config.TinyUrlProperties;
import com.tinyurl.domain.UrlMapping;
import com.tinyurl.domain.UrlRepository;
import com.tinyurl.dto.CreateUrlResponse;
import com.tinyurl.dto.UrlAnalyticsResponse;
import com.tinyurl.exception.InvalidUrlException;
import com.tinyurl.exception.ShortCodeGenerationException;
import com.tinyurl.exception.UrlNotFoundException;
import com.tinyurl.util.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceImplTest {

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private UrlRepository repository;

    @Mock
    private Clock clock;

    @Mock
    private TinyUrlProperties properties;

    @InjectMocks
    private UrlServiceImpl service;

    @Test
    void createShortUrlPersistsMappingAndReturnsResponse() {
        when(shortCodeGenerator.generate()).thenReturn("Ab12xYz");
        when(repository.existsByShortCode("Ab12xYz")).thenReturn(false);
        when(repository.saveAndFlush(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(properties.getBaseUrl()).thenReturn("http://localhost:8080");

        CreateUrlResponse response = service.createShortUrl("https://example.com/articles/1");

        assertEquals("Ab12xYz", response.shortCode());
        assertEquals("http://localhost:8080/Ab12xYz", response.shortUrl());
        assertEquals("https://example.com/articles/1", response.originalUrl());
        assertNotNull(response.createdAt());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), response.createdAt());
        verify(repository).saveAndFlush(any(UrlMapping.class));
    }

    @Test
    void createShortUrlRejectsUnsupportedOrMalformedUrls() {
        assertAll(
                () -> assertThrows(InvalidUrlException.class, () -> service.createShortUrl("ftp://example.com/file")),
                () -> assertThrows(InvalidUrlException.class, () -> service.createShortUrl("not-a-url")),
                () -> assertThrows(InvalidUrlException.class, () -> service.createShortUrl("https://exa mple.com"))
        );
        verifyNoInteractions(shortCodeGenerator, repository);
    }

    @Test
    void createShortUrlRetriesWhenGeneratedCodeAlreadyExists() {
        when(shortCodeGenerator.generate()).thenReturn("taken01", "free002");
        when(repository.existsByShortCode("taken01")).thenReturn(true);
        when(repository.existsByShortCode("free002")).thenReturn(false);
        when(repository.saveAndFlush(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(properties.getBaseUrl()).thenReturn("http://localhost:8080");

        CreateUrlResponse response = service.createShortUrl("https://example.com");

        assertEquals("free002", response.shortCode());
        verify(shortCodeGenerator, times(2)).generate();
    }

    @Test
    void createShortUrlRetriesWhenConcurrentInsertWinsRace() {
        when(shortCodeGenerator.generate()).thenReturn("race001", "free002");
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.saveAndFlush(any(UrlMapping.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate short code"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(properties.getBaseUrl()).thenReturn("http://localhost:8080");

        CreateUrlResponse response = service.createShortUrl("https://example.com");

        assertEquals("free002", response.shortCode());
        verify(shortCodeGenerator, times(2)).generate();
        verify(repository, times(2)).saveAndFlush(any(UrlMapping.class));
    }

    @Test
    void createShortUrlFailsAfterFiveCollisions() {
        when(shortCodeGenerator.generate()).thenReturn("taken01");
        when(repository.existsByShortCode("taken01")).thenReturn(true);

        ShortCodeGenerationException exception = assertThrows(
                ShortCodeGenerationException.class,
                () -> service.createShortUrl("https://example.com"));

        assertEquals("Unable to generate a unique short code", exception.getMessage());
        verify(shortCodeGenerator, times(5)).generate();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void resolveAndRecordRedirectUpdatesAnalyticsData() {
        UrlMapping mapping = new UrlMapping("abc1234", "https://example.com", Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(mapping));
        when(clock.instant()).thenReturn(Instant.parse("2026-01-02T00:00:00Z"));
        when(repository.recordRedirect("abc1234", Instant.parse("2026-01-02T00:00:00Z"))).thenReturn(1);

        String result = service.resolveAndRecordRedirect("abc1234");

        assertEquals("https://example.com", result);
        verify(repository).recordRedirect("abc1234", Instant.parse("2026-01-02T00:00:00Z"));
    }

    @Test
    void getAnalyticsReturnsStoredValues() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant accessedAt = Instant.parse("2026-01-02T00:00:00Z");
        UrlMapping mapping = new UrlMapping("abc1234", "https://example.com", createdAt);
        mapping.recordRedirect(accessedAt);
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(mapping));

        UrlAnalyticsResponse response = service.getAnalytics("abc1234");

        assertEquals("abc1234", response.shortCode());
        assertEquals("https://example.com", response.originalUrl());
        assertEquals(1, response.redirectCount());
        assertEquals(createdAt, response.createdAt());
        assertEquals(accessedAt, response.lastAccessedAt());
    }

    @Test
    void resolveThrowsWhenShortCodeDoesNotExist() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        UrlNotFoundException exception = assertThrows(
                UrlNotFoundException.class,
                () -> service.resolveAndRecordRedirect("missing"));

        assertEquals("Short code not found: missing", exception.getMessage());
    }
}
