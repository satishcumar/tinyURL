package com.tinyurl.orchestration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record RecordCommandRequest(
        @NotBlank String stageId,
        @NotBlank String command,
        @PositiveOrZero int exitCode,
        @NotNull Instant startedAt,
        @PositiveOrZero long durationMillis,
        @NotBlank String outputDigest) {
}
