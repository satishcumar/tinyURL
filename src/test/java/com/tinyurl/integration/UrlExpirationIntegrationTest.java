package com.tinyurl.integration;

import com.tinyurl.domain.UrlMapping;
import com.tinyurl.domain.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class UrlExpirationIntegrationTest {

    private final WebApplicationContext applicationContext;
    private final UrlRepository repository;
    private MockMvc mockMvc;

    @Autowired
    UrlExpirationIntegrationTest(WebApplicationContext applicationContext, UrlRepository repository) {
        this.applicationContext = applicationContext;
        this.repository = repository;
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void createsUrlWithOptionalFutureExpiration() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\","
                                + "\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value("2099-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void rejectsExpirationThatIsNotInTheFuture() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\","
                                + "\"expiresAt\":\"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Expiration must be in the future"));
    }

    @Test
    void expiredUrlReturnsGoneWithoutIncrementingRedirectCount() throws Exception {
        repository.saveAndFlush(new UrlMapping(
                "expired01",
                "https://example.com/expired",
                Instant.parse("1999-01-01T00:00:00Z"),
                Instant.parse("2000-01-01T00:00:00Z")));

        mockMvc.perform(get("/expired01"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("Short URL has expired: expired01"));

        UrlMapping unchanged = repository.findByShortCode("expired01").orElseThrow();
        assertEquals(0, unchanged.getRedirectCount());

        mockMvc.perform(get("/api/v1/urls/expired01/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value("2000-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }
}
