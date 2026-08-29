package com.tinyurl.orchestration.model;

public enum TaskStatus {
    PENDING, READY, RUNNING, WAITING_FOR_DEPENDENCY, SUCCEEDED, FAILED, BLOCKED
}
