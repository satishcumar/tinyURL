package com.tinyurl.orchestration.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkflowRequest(@NotBlank String requirement) {
}
