package com.tinyurl.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UrlRepositoryIntegrationTest {

    private final UrlRepository repository;

    @Autowired
    UrlRepositoryIntegrationTest(UrlRepository repository) {
        this.repository = repository;
    }

    @Test
    void recordRedirectAtomicallyIncrementsCountAndUpdatesTimestamp() {
        UrlMapping mapping = repository.saveAndFlush(new UrlMapping(
                "abc1234",
                "https://example.com",
                Instant.parse("2026-01-01T00:00:00Z")));

        repository.recordRedirect(mapping.getShortCode(), Instant.parse("2026-01-02T00:00:00Z"));
        repository.recordRedirect(mapping.getShortCode(), Instant.parse("2026-01-03T00:00:00Z"));

        UrlMapping updated = repository.findByShortCode(mapping.getShortCode()).orElseThrow();
        assertEquals(2, updated.getRedirectCount());
        assertEquals(Instant.parse("2026-01-03T00:00:00Z"), updated.getLastAccessedAt());
    }

    @Test
    void uniqueConstraintRejectsDuplicateShortCode() {
        repository.saveAndFlush(new UrlMapping(
                "duplicate",
                "https://example.com/first",
                Instant.parse("2026-01-01T00:00:00Z")));

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(new UrlMapping(
                "duplicate",
                "https://example.com/second",
                Instant.parse("2026-01-02T00:00:00Z"))));
    }
}
