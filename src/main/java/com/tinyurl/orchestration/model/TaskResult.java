package com.tinyurl.orchestration.model;

public record TaskResult(boolean successful, FailureClassification failureClassification, String message) {
    public static TaskResult success(String message) {
        return new TaskResult(true, null, message);
    }

    public static TaskResult failure(FailureClassification classification, String message) {
        return new TaskResult(false, classification, message);
    }
}
