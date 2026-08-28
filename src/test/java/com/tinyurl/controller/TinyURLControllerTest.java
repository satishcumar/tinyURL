package com.tinyurl.controller;

import com.tinyurl.dto.CreateUrlResponse;
import com.tinyurl.dto.UrlAnalyticsResponse;
import com.tinyurl.exception.GlobalExceptionHandler;
import com.tinyurl.exception.ShortCodeGenerationException;
import com.tinyurl.exception.UrlNotFoundException;
import com.tinyurl.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TinyURLControllerTest {

    @Mock
    private UrlService urlService;

    @InjectMocks
    private TinyURLController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(
                        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)))
                .build();
    }

    @Test
    void createReturns201AndResponseBody() throws Exception {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(urlService.createShortUrl("https://example.com", null))
                .thenReturn(new CreateUrlResponse(
                        "abc1234",
                        "http://localhost:8080/abc1234",
                        "https://example.com",
                        createdAt,
                        null,
                        "ACTIVE"));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"));
    }

    @Test
    void createReturns400WhenUrlIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("url is required"))
                .andExpect(jsonPath("$.path").value("/api/v1/urls"));
    }

    @Test
    void createReturns503WhenUniqueCodeCannotBeGenerated() throws Exception {
        when(urlService.createShortUrl("https://example.com", null))
                .thenThrow(new ShortCodeGenerationException());

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Unable to generate a unique short code"));
    }

    @Test
    void createReturns400WhenUrlExceedsMaximumLength() throws Exception {
        String oversizedUrl = "https://example.com/" + "a".repeat(2048);

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + oversizedUrl + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("url must be at most 2048 characters"));
    }

    @Test
    void unexpectedFailureReturnsGeneric500WithoutInternalDetails() throws Exception {
        when(urlService.getAnalytics("abc1234"))
                .thenThrow(new IllegalStateException("sensitive internal detail"));

        mockMvc.perform(get("/api/v1/urls/abc1234/analytics"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sensitive internal detail"))));
    }

    @Test
    void redirectReturns302WithLocationHeader() throws Exception {
        when(urlService.resolveAndRecordRedirect("abc1234", null, null)).thenReturn("https://example.com/page");

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"))
                .andExpect(content().string(""));
    }

    @Test
    void analyticsReturnsStoredStatistics() throws Exception {
        when(urlService.getAnalytics("abc1234")).thenReturn(new UrlAnalyticsResponse(
                "abc1234",
                "https://example.com",
                3,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                null,
                "ACTIVE",
                java.util.List.of(),
                java.util.Map.of(),
                java.util.Map.of()));

        mockMvc.perform(get("/api/v1/urls/abc1234/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.redirectCount").value(3))
                .andExpect(jsonPath("$.lastAccessedAt").value("2026-01-02T00:00:00Z"));
    }

    @Test
    void missingShortCodeReturns404ApiError() throws Exception {
        when(urlService.getAnalytics("missing")).thenThrow(new UrlNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/urls/missing/analytics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Short code not found: missing"))
                .andExpect(jsonPath("$.path").value("/api/v1/urls/missing/analytics"));
    }

    @Test
    void deactivateReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/urls/abc1234"))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(urlService).deactivate("abc1234");
    }
}
