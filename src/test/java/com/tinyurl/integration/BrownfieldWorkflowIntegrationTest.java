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
@TestPropertySource(properties = "orchestration.artifact-root=target/brownfield-e2e-runs")
class BrownfieldWorkflowIntegrationTest {
    private final WebApplicationContext applicationContext;
    private MockMvc mockMvc;

    @Autowired
    BrownfieldWorkflowIntegrationTest(WebApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void migrationRequiresSchemaApprovalBeforeExecution() throws Exception {
        String created = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"Replace create-drop with Flyway migrations"
                                + " while preserving existing data\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.analysis.scenario").value("BROWNFIELD"))
                .andExpect(jsonPath("$.analysis.repositoryImpacts.length()").value(5))
                .andExpect(jsonPath("$.taskGraph.length()").value(6))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/v1/workflows/{id}/plan-approval", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\":\"architect\",\"rationale\":\"Plan reviewed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_SCHEMA_APPROVAL"));

        mockMvc.perform(post("/api/v1/workflows/{id}/execution", id))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/workflows/{id}/schema-approval", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\":\"database-owner\","
                                + "\"rationale\":\"Recovery point and migration reviewed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_EXECUTION"));

        mockMvc.perform(post("/api/v1/workflows/{id}/execution", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.metrics.successfulTasks").value(6))
                .andExpect(jsonPath("$.metrics.successRate").value(1.0));
    }
}
