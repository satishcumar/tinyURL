package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.TaskNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DependencyGraphValidator {

    public void validate(List<TaskNode> nodes) {
        Map<String, TaskNode> byId = new HashMap<>();
        for (TaskNode node : nodes) {
            if (byId.put(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate task id: " + node.id());
            }
        }
        for (TaskNode node : nodes) {
            for (String dependency : node.dependsOn()) {
                if (!byId.containsKey(dependency)) {
                    throw new IllegalArgumentException("Unknown dependency " + dependency + " for " + node.id());
                }
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (TaskNode node : nodes) {
            detectCycle(node.id(), byId, visiting, visited);
        }
    }

    private void detectCycle(String id, Map<String, TaskNode> nodes,
                             Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("Dependency cycle detected at task: " + id);
        }
        for (String dependency : nodes.get(id).dependsOn()) {
            detectCycle(dependency, nodes, visiting, visited);
        }
        visiting.remove(id);
        visited.add(id);
    }
}
