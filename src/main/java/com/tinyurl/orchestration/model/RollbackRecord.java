package com.tinyurl.orchestration.model;

import java.time.Instant;

public record RollbackRecord(
        String taskId,
        boolean successful,
        String message,
        Instant startedAt,
        Instant completedAt) {
}
