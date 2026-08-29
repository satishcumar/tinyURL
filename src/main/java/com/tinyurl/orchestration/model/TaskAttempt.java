package com.tinyurl.orchestration.model;

import java.time.Instant;

public record TaskAttempt(
        String taskId,
        int attemptNumber,
        boolean successful,
        FailureClassification failureClassification,
        String message,
        Instant startedAt,
        Instant completedAt,
        String worker) {
}
