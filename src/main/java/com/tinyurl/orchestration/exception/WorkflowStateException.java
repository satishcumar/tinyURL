package com.tinyurl.orchestration.exception;

public class WorkflowStateException extends RuntimeException {
    public WorkflowStateException(String message) {
        super(message);
    }
}
