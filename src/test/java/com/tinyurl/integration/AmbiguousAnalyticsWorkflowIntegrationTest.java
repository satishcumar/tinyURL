package com.tinyurl.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "orchestration.artifact-root=target/analytics-e2e-runs")
class AmbiguousAnalyticsWorkflowIntegrationTest {
    private final WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    @Autowired
    AmbiguousAnalyticsWorkflowIntegrationTest(WebApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void ambiguousAnalyticsStopsForHumanApprovalThenExecutesBoundedPlan() throws Exception {
        String created = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"Provide richer analytics\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.analysis.scenario").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.analysis.ambiguities.length()").value(2))
                .andExpect(jsonPath("$.taskGraph.length()").value(7))
                .andExpect(jsonPath("$.status").value("AWAITING_PLAN_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/v1/workflows/{id}/execution", id))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/workflows/{id}/plan-approval", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\":\"product-and-privacy-owner\","
                                + "\"rationale\":\"Aggregate-only interpretation approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_EXECUTION"));

        mockMvc.perform(post("/api/v1/workflows/{id}/execution", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.metrics.successfulTasks").value(7))
                .andExpect(jsonPath("$.metrics.successRate").value(1.0));
    }
}
