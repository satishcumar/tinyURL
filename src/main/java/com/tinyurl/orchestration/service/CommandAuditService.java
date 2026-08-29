package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.dto.RecordCommandRequest;
import com.tinyurl.orchestration.model.CommandRecord;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class CommandAuditService {
    private final WorkflowService workflowService;
    private final ArtifactStore artifactStore;

    public CommandAuditService(WorkflowService workflowService, ArtifactStore artifactStore) {
        this.workflowService = workflowService;
        this.artifactStore = artifactStore;
    }

    public void record(String executionId, RecordCommandRequest request) {
        workflowService.get(executionId);
        artifactStore.appendCommand(new CommandRecord(
                executionId,
                request.stageId(),
                request.command(),
                request.exitCode(),
                request.startedAt(),
                Duration.ofMillis(request.durationMillis()),
                request.outputDigest()));
    }
}
