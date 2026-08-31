package com.tinyurl.orchestration.model;

import java.util.List;

public record TaskNode(
        String id,
        String name,
        List<String> dependsOn,
        List<String> acceptanceCriteria,
        PolicyAction action,
        TaskStatus status) {

    public TaskNode withStatus(TaskStatus newStatus) {
        return new TaskNode(id, name, dependsOn, acceptanceCriteria, action, newStatus);
    }
}
