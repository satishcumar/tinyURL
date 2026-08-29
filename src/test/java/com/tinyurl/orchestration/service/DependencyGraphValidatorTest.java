package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.PolicyAction;
import com.tinyurl.orchestration.model.TaskNode;
import com.tinyurl.orchestration.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyGraphValidatorTest {
    private final DependencyGraphValidator validator = new DependencyGraphValidator();

    @Test
    void rejectsCycles() {
        List<TaskNode> nodes = List.of(node("a", List.of("b")), node("b", List.of("a")));

        assertThatThrownBy(() -> validator.validate(nodes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectsMissingDependencies() {
        assertThatThrownBy(() -> validator.validate(List.of(node("a", List.of("missing")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown dependency");
    }

    private TaskNode node(String id, List<String> dependencies) {
        return new TaskNode(id, id, dependencies, List.of("AC-1"),
                PolicyAction.CREATE_PLAN, TaskStatus.PENDING);
    }
}
