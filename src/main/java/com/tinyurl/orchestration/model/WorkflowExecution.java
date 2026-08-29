package com.tinyurl.orchestration.model;

import java.time.Instant;
import java.util.List;

public record WorkflowExecution(
        String id,
        String requirement,
        int requirementVersion,
        WorkflowStatus status,
        RequirementAnalysis analysis,
        List<TaskNode> taskGraph,
        List<TaskAttempt> attempts,
        List<RollbackRecord> rollbacks,
        ExecutionMetrics metrics,
        ApprovalRecord planApproval,
        Instant createdAt,
        Instant updatedAt) {

    public WorkflowExecution approve(ApprovalRecord approval, List<TaskNode> updatedGraph, Instant now) {
        return new WorkflowExecution(id, requirement, requirementVersion,
                WorkflowStatus.READY_FOR_EXECUTION, analysis, updatedGraph, attempts, rollbacks, metrics,
                approval, createdAt, now);
    }

    public WorkflowExecution withExecution(WorkflowStatus newStatus, List<TaskNode> graph,
                                           List<TaskAttempt> taskAttempts,
                                           List<RollbackRecord> rollbackRecords,
                                           ExecutionMetrics executionMetrics, Instant now) {
        return new WorkflowExecution(id, requirement, requirementVersion, newStatus, analysis,
                graph, taskAttempts, rollbackRecords, executionMetrics, planApproval, createdAt, now);
    }
}
