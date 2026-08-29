package com.tinyurl.orchestration.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovePlanRequest(
        @NotBlank String approvedBy,
        @NotBlank String rationale) {
}
