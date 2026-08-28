package com.tinyurl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "redirect_event")
public class RedirectEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 12)
    private String shortCode;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "referrer_host", length = 255)
    private String referrerHost;

    @Column(name = "client_category", nullable = false, length = 32)
    private String clientCategory;

    protected RedirectEvent() {
    }

    public RedirectEvent(String shortCode, Instant occurredAt, String referrerHost, String clientCategory) {
        this.shortCode = shortCode;
        this.occurredAt = occurredAt;
        this.referrerHost = referrerHost;
        this.clientCategory = clientCategory;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getReferrerHost() {
        return referrerHost;
    }

    public String getClientCategory() {
        return clientCategory;
    }
}
