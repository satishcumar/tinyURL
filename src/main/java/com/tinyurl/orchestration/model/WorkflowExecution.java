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
        List<ReplanRecord> replans,
        ApprovalRecord planApproval,
        ApprovalRecord schemaApproval,
        Instant createdAt,
        Instant updatedAt) {

    public WorkflowExecution approvePlan(ApprovalRecord approval, WorkflowStatus nextStatus, Instant now) {
        return new WorkflowExecution(id, requirement, requirementVersion, nextStatus, analysis,
                taskGraph, attempts, rollbacks, metrics, replans, approval, schemaApproval, createdAt, now);
    }

    public WorkflowExecution approveSchema(ApprovalRecord approval, Instant now) {
        return new WorkflowExecution(id, requirement, requirementVersion,
                WorkflowStatus.READY_FOR_EXECUTION, analysis, taskGraph, attempts, rollbacks,
                metrics, replans, planApproval, approval, createdAt, now);
    }

    public WorkflowExecution withExecution(WorkflowStatus newStatus, List<TaskNode> graph,
                                           List<TaskAttempt> taskAttempts,
                                           List<RollbackRecord> rollbackRecords,
                                           ExecutionMetrics executionMetrics, Instant now) {
        return new WorkflowExecution(id, requirement, requirementVersion, newStatus, analysis,
                graph, taskAttempts, rollbackRecords, executionMetrics, replans, planApproval,
                schemaApproval, createdAt, now);
    }

    public WorkflowExecution replan(String newRequirement, RequirementAnalysis newAnalysis,
                                    List<TaskNode> newGraph, ReplanRecord record, Instant now) {
        List<ReplanRecord> updated = new java.util.ArrayList<>(replans);
        updated.add(record);
        return new WorkflowExecution(id, newRequirement, requirementVersion + 1,
                WorkflowStatus.AWAITING_PLAN_APPROVAL, newAnalysis, newGraph, attempts, rollbacks,
                ExecutionMetrics.notStarted(newGraph.size()), List.copyOf(updated),
                null, null, createdAt, now);
    }
}
