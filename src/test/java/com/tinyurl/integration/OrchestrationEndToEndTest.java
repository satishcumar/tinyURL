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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItems;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "orchestration.artifact-root=target/day1-e2e-runs")
class OrchestrationEndToEndTest {
    private final WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    @Autowired
    OrchestrationEndToEndTest(WebApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void requirementBecomesApprovedExecutedAndReviewableOutcome() throws Exception {
        String created = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"Add URL expiration and lifecycle management\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PLAN_APPROVAL"))
                .andExpect(jsonPath("$.analysis.acceptanceCriteria.length()").value(5))
                .andExpect(jsonPath("$.taskGraph.length()").value(5))
                .andReturn().getResponse().getContentAsString();

        String executionId = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/v1/workflows/{id}/commands", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stageId":"inspect",
                                  "command":"rg --files src/main src/test",
                                  "exitCode":0,
                                  "startedAt":"2026-08-29T00:00:00Z",
                                  "durationMillis":12,
                                  "outputDigest":"sha256:day1-evidence"
                                }
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/workflows/{id}/plan-approval", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\":\"day1-reviewer\","
                                + "\"rationale\":\"Acceptance criteria and risks reviewed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_EXECUTION"));

        mockMvc.perform(post("/api/v1/workflows/{id}/execution", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.metrics.successRate").value(1.0))
                .andExpect(jsonPath("$.metrics.successfulTasks").value(5))
                .andExpect(jsonPath("$.metrics.safeStopCount").value(0))
                .andExpect(jsonPath("$.attempts.length()").value(5));

        mockMvc.perform(get("/api/v1/workflows/{id}/artifacts", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasItems(
                        "workflow.json", "events.jsonl", "commands.jsonl", "metrics.json",
                        "traceability-matrix.md", "engineering-summary.md")));
    }
}
