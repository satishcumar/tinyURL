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
import static org.mockito.Mockito.mock;

class WorkflowServiceTest {
    @TempDir
    Path artifacts;

    @Test
    void createsPlanAndStopsAtApprovalGate() {
        WorkflowService service = service();

        var execution = service.create("Add URL expiration and lifecycle management");

        assertThat(execution.status()).isEqualTo(WorkflowStatus.AWAITING_PLAN_APPROVAL);
        assertThat(execution.taskGraph()).hasSize(5);
        assertThat(service.artifacts(execution.id())).contains(
                "workflow.json", "events.jsonl", "requirement.md",
                "normalized-requirement.json", "acceptance-criteria.json",
                "dependency-graph.json", "plan.md", "approvals.json", "decisions.json",
                "command-audit.jsonl", "changed-files.json", "test-results.json",
                "traceability-matrix.md", "risk-report.md", "metrics.json",
                "engineering-summary.md");
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

    @Test
    void requiresSeparateSchemaApprovalForBrownfieldMigration() {
        WorkflowService service = service();
        var execution = service.create(
                "Replace create-drop with Flyway migrations while preserving existing data");

        var planApproved = service.approvePlan(execution.id(), "architect", "Plan reviewed");
        assertThat(planApproved.status()).isEqualTo(WorkflowStatus.AWAITING_SCHEMA_APPROVAL);

        var schemaApproved = service.approveSchemaChange(
                execution.id(), "database-owner", "Backup and migration reviewed");
        assertThat(schemaApproved.status()).isEqualTo(WorkflowStatus.READY_FOR_EXECUTION);
        assertThat(schemaApproved.schemaApproval().approvedBy()).isEqualTo("database-owner");
    }

    @Test
    void replanningInvalidatesChangedTaskAndEveryDescendant() {
        WorkflowService service = service();
        var execution = service.create(
                "Replace create-drop with Flyway migrations while preserving existing data");

        var replanned = service.replan(execution.id(),
                "Replace create-drop with Flyway migrations and preserve all data",
                java.util.List.of("assess-schema"), "Schema assumption changed");

        assertThat(replanned.requirementVersion()).isEqualTo(2);
        assertThat(replanned.status()).isEqualTo(WorkflowStatus.AWAITING_PLAN_APPROVAL);
        assertThat(replanned.replans()).singleElement().satisfies(record ->
                assertThat(record.invalidatedTaskIds()).containsExactly(
                        "assess-schema", "recovery-point", "migration", "preservation-test", "validate"));
    }

    private WorkflowService service() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        ArtifactStore store = new ArtifactStore(mapper, artifacts.toString());
        DependencyGraphValidator validator = new DependencyGraphValidator();
        return new WorkflowService(new RequirementAnalyzer(), new WorkflowPlanner(validator),
                new PolicyEngine(), store, mock(WorkflowExecutionEngine.class),
                mock(UrlExpirationTaskRunner.class), new DependencyInvalidationService(),
                Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC));
    }
}
