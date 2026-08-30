package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.ExecutionMetrics;
import com.tinyurl.orchestration.model.RequirementAnalysis;
import com.tinyurl.orchestration.model.ScenarioType;
import com.tinyurl.orchestration.model.TaskNode;
import com.tinyurl.orchestration.model.TaskStatus;
import com.tinyurl.orchestration.model.PolicyAction;
import com.tinyurl.orchestration.model.WorkflowExecution;
import com.tinyurl.orchestration.model.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactStoreTest {
    @TempDir
    Path root;

    @Test
    void exportsCompleteReviewPackageWithoutInventingGitOrExternalTestEvidence() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        ArtifactStore store = new ArtifactStore(mapper, root.toString());
        String id = UUID.randomUUID().toString();
        RequirementAnalysis analysis = new RequirementAnalysis(
                ScenarioType.GREENFIELD, "Add governed URL expiration", List.of(),
                List.of("UTC is used"), List.of(), List.of("Clock skew"), List.of());
        TaskNode validate = new TaskNode("validate", "Run validation", List.of(), List.of(),
                PolicyAction.RUN_LOCAL_TESTS, TaskStatus.PENDING);
        WorkflowExecution execution = new WorkflowExecution(
                id, "Add URL expiration", 1, WorkflowStatus.AWAITING_PLAN_APPROVAL,
                analysis, List.of(validate), List.of(), List.of(),
                ExecutionMetrics.notStarted(1), List.of(), null, null,
                Instant.parse("2026-08-30T00:00:00Z"), Instant.parse("2026-08-30T00:00:00Z"));

        store.saveSnapshot(execution);

        assertThat(store.listArtifacts(id)).contains(
                "requirement.md", "normalized-requirement.json", "acceptance-criteria.json",
                "dependency-graph.json", "plan.md", "approvals.json", "decisions.json",
                "command-audit.jsonl", "changed-files.json", "test-results.json",
                "traceability-matrix.md", "risk-report.md", "metrics.json",
                "engineering-summary.md", "workflow.json");
        Path directory = root.resolve(id);
        assertThat(mapper.readTree(directory.resolve("changed-files.json").toFile())
                .get("actualGitDiffCaptured").asBoolean()).isFalse();
        assertThat(mapper.readTree(directory.resolve("dependency-graph.json").toFile())
                .get("nodes").size()).isEqualTo(1);
        assertThat(Files.readString(directory.resolve("plan.md"))).contains("Run validation");
        assertThat(Files.size(directory.resolve("command-audit.jsonl"))).isZero();
    }
}
