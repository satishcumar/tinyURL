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
        ApprovalRecord planApproval,
        Instant createdAt,
        Instant updatedAt) {

    public WorkflowExecution approve(ApprovalRecord approval, List<TaskNode> updatedGraph, Instant now) {
        return new WorkflowExecution(id, requirement, requirementVersion,
                WorkflowStatus.READY_FOR_EXECUTION, analysis, updatedGraph, approval, createdAt, now);
    }
}
