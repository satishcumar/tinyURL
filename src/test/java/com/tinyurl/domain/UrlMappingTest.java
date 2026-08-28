package com.tinyurl.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UrlMappingTest {

    @Test
    void recordRedirectIncrementsCountAndStoresLatestAccessTime() {
        UrlMapping mapping = new UrlMapping("abc1234", "https://example.com", Instant.parse("2026-01-01T00:00:00Z"));
        Instant firstAccess = Instant.parse("2026-01-02T00:00:00Z");
        Instant secondAccess = Instant.parse("2026-01-03T00:00:00Z");

        mapping.recordRedirect(firstAccess);
        mapping.recordRedirect(secondAccess);

        assertEquals(2, mapping.getRedirectCount());
        assertEquals(secondAccess, mapping.getLastAccessedAt());
    }
}
