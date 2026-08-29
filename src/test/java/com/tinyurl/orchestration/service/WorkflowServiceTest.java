package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.exception.WorkflowStateException;
import com.tinyurl.orchestration.model.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowServiceTest {
    @TempDir
    Path artifacts;

    @Test
    void createsPlanAndStopsAtApprovalGate() {
        WorkflowService service = service();

        var execution = service.create("Add URL expiration and lifecycle management");

        assertThat(execution.status()).isEqualTo(WorkflowStatus.AWAITING_PLAN_APPROVAL);
        assertThat(execution.taskGraph()).hasSize(5);
        assertThat(service.artifacts(execution.id())).contains("workflow.json", "events.jsonl");
    }

    @Test
    void recordsHumanApprovalAndPreventsDuplicateApproval() {
        WorkflowService service = service();
        var execution = service.create("Add URL expiration");

        var approved = service.approvePlan(execution.id(), "reviewer", "Scope and risks accepted");

        assertThat(approved.status()).isEqualTo(WorkflowStatus.READY_FOR_EXECUTION);
        assertThat(approved.planApproval().approvedBy()).isEqualTo("reviewer");
        assertThatThrownBy(() -> service.approvePlan(execution.id(), "reviewer", "again"))
                .isInstanceOf(WorkflowStateException.class);
    }

    private WorkflowService service() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        ArtifactStore store = new ArtifactStore(mapper, artifacts.toString());
        DependencyGraphValidator validator = new DependencyGraphValidator();
        return new WorkflowService(new RequirementAnalyzer(), new WorkflowPlanner(validator),
                new PolicyEngine(), store,
                Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC));
    }
}
