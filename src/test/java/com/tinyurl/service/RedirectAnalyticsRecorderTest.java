package com.tinyurl.service;

import com.tinyurl.domain.RedirectEvent;
import com.tinyurl.domain.RedirectEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedirectAnalyticsRecorderTest {

    @Mock
    private RedirectEventRepository repository;

    @Test
    void storesOnlyReferrerHostAndCoarseClientCategory() {
        RedirectAnalyticsRecorder recorder = new RedirectAnalyticsRecorder(repository);
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        recorder.record(
                "abc1234",
                occurredAt,
                "https://search.example/results?q=private-value",
                "Mozilla/5.0 (iPhone; sensitive device details) Mobile");

        ArgumentCaptor<RedirectEvent> captor = ArgumentCaptor.forClass(RedirectEvent.class);
        verify(repository).save(captor.capture());
        assertEquals(occurredAt, captor.getValue().getOccurredAt());
        assertEquals("search.example", captor.getValue().getReferrerHost());
        assertEquals("MOBILE", captor.getValue().getClientCategory());
    }

    @Test
    void doesNotFailOnMissingOrMalformedHeaders() {
        RedirectAnalyticsRecorder recorder = new RedirectAnalyticsRecorder(repository);

        recorder.record("abc1234", Instant.EPOCH, "not a uri", null);

        ArgumentCaptor<RedirectEvent> captor = ArgumentCaptor.forClass(RedirectEvent.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getReferrerHost());
        assertEquals("UNKNOWN", captor.getValue().getClientCategory());
    }
}
