package com.tinyurl.orchestration.model;

import java.time.Duration;
import java.time.Instant;

public record CommandRecord(
        String executionId,
        String stageId,
        String command,
        int exitCode,
        Instant startedAt,
        Duration duration,
        String outputDigest) {
}
