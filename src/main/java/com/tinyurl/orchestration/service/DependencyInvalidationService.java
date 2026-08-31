package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.TaskNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class DependencyInvalidationService {
    public List<String> invalidate(List<TaskNode> graph, List<String> changedTaskIds) {
        Set<String> known = graph.stream().map(TaskNode::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!known.containsAll(changedTaskIds)) {
            Set<String> unknown = new LinkedHashSet<>(changedTaskIds);
            unknown.removeAll(known);
            throw new IllegalArgumentException("Unknown changed tasks: " + unknown);
        }
        Set<String> invalidated = new LinkedHashSet<>(changedTaskIds);
        boolean changed;
        do {
            changed = false;
            for (TaskNode task : graph) {
                if (!invalidated.contains(task.id()) && task.dependsOn().stream().anyMatch(invalidated::contains)) {
                    changed |= invalidated.add(task.id());
                }
            }
        } while (changed);
        return List.copyOf(invalidated);
    }
}
