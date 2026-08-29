package com.tinyurl.orchestration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReplanWorkflowRequest(
        @NotBlank String requirement,
        @NotEmpty List<String> changedTaskIds,
        @NotBlank String rationale) {
}
