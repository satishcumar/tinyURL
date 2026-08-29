package com.tinyurl.orchestration.service;

import tools.jackson.databind.ObjectMapper;
import com.tinyurl.orchestration.model.AuditEvent;
import com.tinyurl.orchestration.model.CommandRecord;
import com.tinyurl.orchestration.model.WorkflowExecution;
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
}
