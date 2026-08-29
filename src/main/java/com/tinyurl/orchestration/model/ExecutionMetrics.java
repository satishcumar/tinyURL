package com.tinyurl.orchestration.model;

import java.time.Instant;

public record ExecutionMetrics(
        int totalTasks,
        int successfulTasks,
        int failedTasks,
        int retryCount,
        int rollbackCount,
        int safeStopCount,
        double successRate,
        double retryFrequency,
        double rollbackFrequency,
        long meanTimeToRepairMillis,
        long endToEndLatencyMillis,
        Instant startedAt,
        Instant completedAt) {

    public static ExecutionMetrics notStarted(int totalTasks) {
        return new ExecutionMetrics(totalTasks, 0, 0, 0, 0, 0,
                0.0, 0.0, 0.0, 0, 0, null, null);
    }
}
