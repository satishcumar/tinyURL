package com.tinyurl.orchestration.model;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
        String executionId,
        String eventType,
        String actor,
        Instant timestamp,
        Map<String, Object> details) {
}
