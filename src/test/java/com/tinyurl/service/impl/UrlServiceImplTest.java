package com.tinyurl.service.impl;

import com.tinyurl.domain.UrlMapping;
import com.tinyurl.domain.UrlRepository;
import com.tinyurl.dto.CreateUrlResponse;
import com.tinyurl.dto.UrlAnalyticsResponse;
import com.tinyurl.exception.InvalidUrlException;
import com.tinyurl.exception.UrlNotFoundException;
import com.tinyurl.util.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private UrlServiceImpl service;

    @Test
    void createShortUrlPersistsMappingAndReturnsResponse() {
        when(shortCodeGenerator.generate()).thenReturn("Ab12xYz");
        when(repository.existsByShortCode("Ab12xYz")).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateUrlResponse response = service.createShortUrl("https://example.com/articles/1");

        assertEquals("Ab12xYz", response.shortCode());
        assertEquals("http://localhost:8080/Ab12xYz", response.shortUrl());
        assertEquals("https://example.com/articles/1", response.originalUrl());
        assertNotNull(response.createdAt());
        verify(repository).save(any(UrlMapping.class));
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
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateUrlResponse response = service.createShortUrl("https://example.com");

        assertEquals("free002", response.shortCode());
        verify(shortCodeGenerator, times(2)).generate();
    }

    @Test
    void createShortUrlFailsAfterFiveCollisions() {
        when(shortCodeGenerator.generate()).thenReturn("taken01");
        when(repository.existsByShortCode("taken01")).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.createShortUrl("https://example.com"));

        assertEquals("Unable to generate a unique short code", exception.getMessage());
        verify(shortCodeGenerator, times(5)).generate();
        verify(repository, never()).save(any());
    }

    @Test
    void resolveAndRecordRedirectUpdatesAnalyticsData() {
        UrlMapping mapping = new UrlMapping("abc1234", "https://example.com", Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findByShortCode("abc1234")).thenReturn(Optional.of(mapping));

        String result = service.resolveAndRecordRedirect("abc1234");

        assertEquals("https://example.com", result);
        assertEquals(1, mapping.getRedirectCount());
        assertNotNull(mapping.getLastAccessedAt());
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
