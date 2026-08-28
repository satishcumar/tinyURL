package com.tinyurl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "url_mapping", uniqueConstraints = @UniqueConstraint(name = "uk_url_mapping_short_code", columnNames = "short_code"))
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 12)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "redirect_count", nullable = false)
    private long redirectCount;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    protected UrlMapping() {
    }

    public UrlMapping(String shortCode, String originalUrl, Instant createdAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
        this.redirectCount = 0;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getRedirectCount() {
        return redirectCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void recordRedirect(Instant accessedAt) {
        this.redirectCount++;
        this.lastAccessedAt = accessedAt;
    }
}
