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
import java.util.Optional;

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
        writeAtomically(executionDirectory(execution.id()).resolve("workflow.json"), execution);
    }

    public synchronized void appendEvent(AuditEvent event) {
        appendJsonLine(executionDirectory(event.executionId()).resolve("events.jsonl"), event);
    }

    public synchronized void appendCommand(CommandRecord command) {
        appendJsonLine(executionDirectory(command.executionId()).resolve("commands.jsonl"), command);
    }

    public synchronized void saveOutcomeArtifacts(WorkflowExecution execution) {
        Path directory = executionDirectory(execution.id());
        writeAtomically(directory.resolve("metrics.json"), execution.metrics());
        writeTextAtomically(directory.resolve("traceability-matrix.md"), traceability(execution));
        writeTextAtomically(directory.resolve("engineering-summary.md"), engineeringSummary(execution));
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
