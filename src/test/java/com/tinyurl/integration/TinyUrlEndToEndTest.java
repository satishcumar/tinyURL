package com.tinyurl.integration;

import com.jayway.jsonpath.JsonPath;
import com.tinyurl.domain.UrlRepository;
import com.tinyurl.domain.RedirectEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class TinyUrlEndToEndTest {

    private final WebApplicationContext applicationContext;
    private final UrlRepository repository;
    private final RedirectEventRepository redirectEventRepository;
    private MockMvc mockMvc;

    @Autowired
    TinyUrlEndToEndTest(
            WebApplicationContext applicationContext,
            UrlRepository repository,
            RedirectEventRepository redirectEventRepository) {
        this.applicationContext = applicationContext;
        this.repository = repository;
        this.redirectEventRepository = redirectEventRepository;
    }

    @BeforeEach
    void setUp() {
        redirectEventRepository.deleteAll();
        repository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void createRedirectAndAnalyticsWorkEndToEnd() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/products/42\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/products/42"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shortCode = JsonPath.read(responseBody, "$.shortCode");

        mockMvc.perform(get("/{shortCode}", shortCode)
                        .header("Referer", "https://search.example/results?q=tinyurl")
                        .header("User-Agent", "Mozilla/5.0 Mobile"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/products/42"));

        mockMvc.perform(get("/api/v1/urls/{shortCode}/analytics", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(shortCode))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/products/42"))
                .andExpect(jsonPath("$.redirectCount").value(1))
                .andExpect(jsonPath("$.lastAccessedAt").isNotEmpty())
                .andExpect(jsonPath("$.dailyRedirects[0].count").value(1))
                .andExpect(jsonPath("$.clientCategories.MOBILE").value(1))
                .andExpect(jsonPath("$.referrerHosts['search.example']").value(1));
    }

    @Test
    void deactivatedUrlReturnsGoneWhileAnalyticsRemainAvailable() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value("2099-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        String shortCode = JsonPath.read(responseBody, "$.shortCode");

        mockMvc.perform(delete("/api/v1/urls/{shortCode}", shortCode))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));

        mockMvc.perform(get("/api/v1/urls/{shortCode}/analytics", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }
}
