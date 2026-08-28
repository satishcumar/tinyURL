package com.tinyurl.integration;

import com.jayway.jsonpath.JsonPath;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class TinyUrlEndToEndTest {

    private final WebApplicationContext applicationContext;
    private final UrlRepository repository;
    private MockMvc mockMvc;

    @Autowired
    TinyUrlEndToEndTest(WebApplicationContext applicationContext, UrlRepository repository) {
        this.applicationContext = applicationContext;
        this.repository = repository;
    }

    @BeforeEach
    void setUp() {
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

        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/products/42"));

        mockMvc.perform(get("/api/v1/urls/{shortCode}/analytics", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(shortCode))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/products/42"))
                .andExpect(jsonPath("$.redirectCount").value(1))
                .andExpect(jsonPath("$.lastAccessedAt").isNotEmpty());
    }
}
