package com.tinyurl.orchestration.service;

import tools.jackson.databind.ObjectMapper;
import com.tinyurl.orchestration.model.AuditEvent;
import com.tinyurl.orchestration.model.CommandRecord;
import com.tinyurl.orchestration.model.WorkflowExecution;
import com.tinyurl.orchestration.model.TaskNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ArtifactStore {
    private final ObjectMapper objectMapper;
    private final Path root;

    public ArtifactStore(ObjectMapper objectMapper,
                         @Value("${orchestration.artifact-root:build/orchestration-runs}") String root) {
        this.objectMapper = objectMapper;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public synchronized void saveSnapshot(WorkflowExecution execution) {
        Path directory = executionDirectory(execution.id());
        writeAtomically(directory.resolve("workflow.json"), execution);
        writeReviewPackage(execution, directory);
    }

    public synchronized void appendEvent(AuditEvent event) {
        appendJsonLine(executionDirectory(event.executionId()).resolve("events.jsonl"), event);
    }

    public synchronized void appendCommand(CommandRecord command) {
        appendJsonLine(executionDirectory(command.executionId()).resolve("commands.jsonl"), command);
        appendJsonLine(executionDirectory(command.executionId()).resolve("command-audit.jsonl"), command);
    }

    public synchronized void saveOutcomeArtifacts(WorkflowExecution execution) {
        Path directory = executionDirectory(execution.id());
        writeReviewPackage(execution, directory);
        writeAtomically(directory.resolve("metrics.json"), execution.metrics());
        writeTextAtomically(directory.resolve("traceability-matrix.md"), traceability(execution));
        writeTextAtomically(directory.resolve("engineering-summary.md"), engineeringSummary(execution));
    }

    private void writeReviewPackage(WorkflowExecution execution, Path directory) {
        writeTextAtomically(directory.resolve("requirement.md"), requirementDocument(execution));
        writeAtomically(directory.resolve("normalized-requirement.json"), Map.of(
                "executionId", execution.id(),
                "requirementVersion", execution.requirementVersion(),
                "scenario", execution.analysis().scenario(),
                "normalizedRequirement", execution.analysis().normalizedRequirement(),
                "assumptions", execution.analysis().assumptions(),
                "ambiguities", execution.analysis().ambiguities()));
        writeAtomically(directory.resolve("acceptance-criteria.json"),
                execution.analysis().acceptanceCriteria());
        writeAtomically(directory.resolve("dependency-graph.json"), dependencyGraph(execution));
        writeTextAtomically(directory.resolve("plan.md"), planDocument(execution));
        writeAtomically(directory.resolve("approvals.json"), approvals(execution));
        writeAtomically(directory.resolve("decisions.json"), decisions(execution));
        ensureFile(directory.resolve("command-audit.jsonl"));
        writeAtomically(directory.resolve("changed-files.json"), changedFiles(execution));
        writeAtomically(directory.resolve("test-results.json"), testResults(execution));
        writeTextAtomically(directory.resolve("risk-report.md"), riskReport(execution));
        writeAtomically(directory.resolve("metrics.json"), execution.metrics());
        writeTextAtomically(directory.resolve("traceability-matrix.md"), traceability(execution));
        writeTextAtomically(directory.resolve("engineering-summary.md"), engineeringSummary(execution));
    }

    private Map<String, Object> dependencyGraph(WorkflowExecution execution) {
        List<Map<String, String>> edges = execution.taskGraph().stream()
                .flatMap(task -> task.dependsOn().stream().map(dependency ->
                        Map.of("from", dependency, "to", task.id())))
                .toList();
        return Map.of("nodes", execution.taskGraph(), "edges", edges);
    }

    private Map<String, Object> approvals(WorkflowExecution execution) {
        Map<String, Object> approvals = new LinkedHashMap<>();
        approvals.put("planApproval", execution.planApproval());
        approvals.put("schemaApproval", execution.schemaApproval());
        approvals.put("currentStatus", execution.status());
        return approvals;
    }

    private Map<String, Object> decisions(WorkflowExecution execution) {
        return Map.of(
                "requirementVersion", execution.requirementVersion(),
                "scenarioSelection", execution.analysis().scenario(),
                "replans", execution.replans(),
                "rollbacks", execution.rollbacks(),
                "decisionLineageNote", "Approval and state-transition details are retained in events.jsonl");
    }

    private Map<String, Object> changedFiles(WorkflowExecution execution) {
        return Map.of(
                "captureMode", "repository-impact-analysis",
                "actualGitDiffCaptured", false,
                "limitations", "The deterministic prototype does not mutate a checked-out repository; declared impacts are reported instead of claiming an observed Git diff.",
                "declaredImpacts", execution.analysis().repositoryImpacts());
    }

    private Map<String, Object> testResults(WorkflowExecution execution) {
        Set<String> validationTasks = execution.taskGraph().stream()
                .filter(task -> task.action() == com.tinyurl.orchestration.model.PolicyAction.GENERATE_TESTS
                        || task.action() == com.tinyurl.orchestration.model.PolicyAction.RUN_LOCAL_TESTS)
                .map(TaskNode::id).collect(java.util.stream.Collectors.toSet());
        List<com.tinyurl.orchestration.model.TaskAttempt> attempts = execution.attempts().stream()
                .filter(attempt -> validationTasks.contains(attempt.taskId())).toList();
        return Map.of(
                "captureMode", "orchestration-task-attempts",
                "externalReportParsed", false,
                "status", execution.status(),
                "validationTaskIds", validationTasks,
                "attempts", attempts);
    }

    private String requirementDocument(WorkflowExecution execution) {
        return "# Requirement\n\n" + safeMarkdown(execution.requirement()) + "\n\n" +
                "- Execution: `" + execution.id() + "`\n" +
                "- Version: " + execution.requirementVersion() + "\n" +
                "- Scenario: `" + execution.analysis().scenario() + "`\n";
    }

    private String planDocument(WorkflowExecution execution) {
        StringBuilder document = new StringBuilder("# Execution plan\n\n")
                .append("- Status: `").append(execution.status()).append("`\n")
                .append("- Requirement version: ").append(execution.requirementVersion()).append("\n\n")
                .append("| Task | Purpose | Dependencies | Policy action | Status |\n")
                .append("|---|---|---|---|---|\n");
        execution.taskGraph().forEach(task -> document.append("| `").append(task.id()).append("` | ")
                .append(safeMarkdown(task.name())).append(" | ")
                .append(task.dependsOn().isEmpty() ? "None" : String.join(", ", task.dependsOn()))
                .append(" | `").append(task.action()).append("` | `")
                .append(task.status()).append("` |\n"));
        return document.toString();
    }

    private String riskReport(WorkflowExecution execution) {
        return "# Risk report\n\n## Identified risks\n\n" +
                markdownList(execution.analysis().risks()) +
                "\n## Ambiguities\n\n" + markdownList(execution.analysis().ambiguities()) +
                "\n## Repository impact risks\n\n" + execution.analysis().repositoryImpacts().stream()
                .map(impact -> "- **" + safeMarkdown(impact.component()) + "**: " +
                        safeMarkdown(impact.risk()) + " — " + safeMarkdown(impact.impact()) + "\n")
                .collect(java.util.stream.Collectors.joining()) +
                "\n## Recovery evidence\n\n- Rollback records: " + execution.rollbacks().size() +
                "\n- Safe stops: " + execution.metrics().safeStopCount() +
                "\n- Retries: " + execution.metrics().retryCount() + "\n";
    }

    private void ensureFile(Path target) {
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                Files.createFile(target);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize audit artifact", exception);
        }
    }

    public List<String> listArtifacts(String executionId) {
        Path directory = executionDirectory(executionId);
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to list workflow artifacts", exception);
        }
    }

    public Optional<WorkflowExecution> loadSnapshot(String executionId) {
        Path snapshot = executionDirectory(executionId).resolve("workflow.json");
        if (!Files.exists(snapshot)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(snapshot.toFile(), WorkflowExecution.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load workflow snapshot", exception);
        }
    }

    private Path executionDirectory(String executionId) {
        if (!executionId.matches("[a-f0-9\\-]{36}")) {
            throw new IllegalArgumentException("Invalid execution id");
        }
        Path directory = root.resolve(executionId).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("Invalid artifact path");
        }
        return directory;
    }

    private void writeAtomically(Path target, Object value) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist workflow artifact", exception);
        }
    }

    private void appendJsonLine(Path target, Object value) {
        try {
            Files.createDirectories(target.getParent());
            String line = objectMapper.writeValueAsString(value) + System.lineSeparator();
            Files.writeString(target, line, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist audit record", exception);
        }
    }

    private void writeTextAtomically(Path target, String value) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist outcome document", exception);
        }
    }

    private String traceability(WorkflowExecution execution) {
        StringBuilder document = new StringBuilder("# Acceptance-criteria traceability\n\n")
                .append("| Criterion | Requirement | Tasks | Evidence status |\n")
                .append("|---|---|---|---|\n");
        execution.analysis().acceptanceCriteria().forEach(criterion -> {
            List<TaskNode> tasks = execution.taskGraph().stream()
                    .filter(task -> task.acceptanceCriteria().contains(criterion.id())).toList();
            String taskIds = tasks.stream().map(TaskNode::id)
                    .collect(java.util.stream.Collectors.joining(", "));
            boolean passed = !tasks.isEmpty() && tasks.stream()
                    .allMatch(task -> task.status() == com.tinyurl.orchestration.model.TaskStatus.SUCCEEDED);
            document.append("| ").append(criterion.id()).append(" | ")
                    .append(safeMarkdown(criterion.description())).append(" | ")
                    .append(taskIds).append(" | ").append(passed ? "PASS" : "NOT VERIFIED")
                    .append(" |\n");
        });
        return document.toString();
    }

    private String engineeringSummary(WorkflowExecution execution) {
        return "# Engineering outcome\n\n" +
                "- Execution: `" + execution.id() + "`\n" +
                "- Requirement version: " + execution.requirementVersion() + "\n" +
                "- Scenario: `" + execution.analysis().scenario() + "`\n" +
                "- Final status: `" + execution.status() + "`\n" +
                "- Tasks passed: " + execution.metrics().successfulTasks() + "/" +
                execution.metrics().totalTasks() + "\n" +
                "- Retries: " + execution.metrics().retryCount() + "\n" +
                "- Rollbacks: " + execution.metrics().rollbackCount() + "\n" +
                "- End-to-end latency (ms): " + execution.metrics().endToEndLatencyMillis() + "\n\n" +
                "## Rationale\n\n" + execution.analysis().normalizedRequirement() + "\n\n" +
                "## Repository impact\n\n" + execution.analysis().repositoryImpacts().stream()
                        .map(impact -> "- **" + safeMarkdown(impact.component()) + "**: " +
                                safeMarkdown(impact.impact()) + " — Risk: " +
                                safeMarkdown(impact.risk()) + "\n")
                        .collect(java.util.stream.Collectors.joining()) +
                "## Assumptions\n\n" + markdownList(execution.analysis().assumptions()) +
                "\n## Risks\n\n" + markdownList(execution.analysis().risks()) +
                "\n## Governance evidence\n\n" +
                "- Plan approval: " + approvalEvidence(execution.planApproval()) + "\n" +
                "- Schema approval: " + approvalEvidence(execution.schemaApproval()) + "\n" +
                "- Replans recorded: " + execution.replans().size() + "\n" +
                "- Recovery-point attempts: " + execution.attempts().stream()
                        .filter(attempt -> attempt.taskId().equals("recovery-point")).count() + "\n" +
                "\n## Limitations\n\n" +
                "- Requirement analysis uses a deterministic prototype adapter.\n" +
                "- Execution is synchronous and filesystem-backed.\n" +
                "- Approver identity is asserted by the caller and is not authenticated.\n";
    }

    private String markdownList(List<String> values) {
        if (values.isEmpty()) {
            return "- None recorded.\n";
        }
        return values.stream().map(value -> "- " + safeMarkdown(value) + "\n")
                .collect(java.util.stream.Collectors.joining());
    }

    private String safeMarkdown(String value) {
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private String approvalEvidence(com.tinyurl.orchestration.model.ApprovalRecord approval) {
        return approval == null ? "not required/not recorded" :
                "approved by " + safeMarkdown(approval.approvedBy()) + " at " + approval.approvedAt();
    }
}
