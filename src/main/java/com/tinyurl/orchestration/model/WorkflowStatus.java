package com.tinyurl.orchestration.model;

public enum WorkflowStatus {
    RECEIVED,
    ANALYZING,
    PLANNING,
    AWAITING_PLAN_APPROVAL,
    READY_FOR_EXECUTION,
    RUNNING,
    VALIDATING,
    COMPLETED,
    BLOCKED,
    SAFE_STOPPED
}
