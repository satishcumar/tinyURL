package com.tinyurl.orchestration.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyInvalidationServiceTest {
    private final DependencyInvalidationService service = new DependencyInvalidationService();

    @Test
    void invalidatesChangedNodeAndTransitiveDependentsOnly() {
        var analysis = new RequirementAnalyzer().analyze(
                "Replace create-drop with Flyway while preserving data");
        var graph = new WorkflowPlanner(new DependencyGraphValidator()).plan(analysis);

        assertThat(service.invalidate(graph, List.of("recovery-point")))
                .containsExactly("recovery-point", "migration", "validate");
    }

    @Test
    void rejectsUnknownChangedTask() {
        var graph = new WorkflowPlanner(new DependencyGraphValidator()).plan(
                new RequirementAnalyzer().analyze("Add URL expiration"));

        assertThatThrownBy(() -> service.invalidate(graph, List.of("missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown changed tasks");
    }
}
