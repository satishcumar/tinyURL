package com.tinyurl.orchestration.model;

import java.time.Instant;

public record ApprovalRecord(String approvedBy, String rationale, Instant approvedAt) {
}
