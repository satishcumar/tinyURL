package com.tinyurl.orchestration.model;

import java.time.Instant;
import java.util.List;

public record ReplanRecord(
        int fromRequirementVersion,
        int toRequirementVersion,
        List<String> changedTaskIds,
        List<String> invalidatedTaskIds,
        String rationale,
        Instant replannedAt) {
}
