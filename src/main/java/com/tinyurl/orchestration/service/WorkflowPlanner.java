package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.PolicyAction;
import com.tinyurl.orchestration.model.RequirementAnalysis;
import com.tinyurl.orchestration.model.TaskNode;
import com.tinyurl.orchestration.model.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WorkflowPlanner {
    private final DependencyGraphValidator graphValidator;

    public WorkflowPlanner(DependencyGraphValidator graphValidator) {
        this.graphValidator = graphValidator;
    }

    public List<TaskNode> plan(RequirementAnalysis analysis) {
        if (analysis.scenario() == com.tinyurl.orchestration.model.ScenarioType.BROWNFIELD) {
            return migrationPlan(analysis);
        }
        List<TaskNode> graph = List.of(
                node("inspect", "Inspect API, service, persistence, and tests", List.of(),
                        List.of("AC-1", "AC-5"), PolicyAction.INSPECT_REPOSITORY),
                node("design", "Define expiration contract and failure semantics", List.of("inspect"),
                        List.of("AC-1", "AC-3", "AC-4"), PolicyAction.CHANGE_PUBLIC_API),
                node("implement", "Implement expiration lifecycle behavior", List.of("design"),
                        List.of("AC-1", "AC-2", "AC-3", "AC-5"), PolicyAction.EDIT_FEATURE_CODE),
                node("test-design", "Create unit and integration validation", List.of("design"),
                        analysis.acceptanceCriteria().stream().map(c -> c.id()).toList(), PolicyAction.GENERATE_TESTS),
                node("validate", "Run build and acceptance tests", List.of("implement", "test-design"),
                        analysis.acceptanceCriteria().stream().map(c -> c.id()).toList(), PolicyAction.RUN_LOCAL_TESTS));
        graphValidator.validate(graph);
        return graph;
    }

    private List<TaskNode> migrationPlan(RequirementAnalysis analysis) {
        List<String> allCriteria = analysis.acceptanceCriteria().stream().map(c -> c.id()).toList();
        List<TaskNode> graph = List.of(
                node("inspect", "Analyze repository schema ownership and data flow", List.of(),
                        List.of("AC-2", "AC-3"), PolicyAction.INSPECT_REPOSITORY),
                node("assess-schema", "Compare entity, existing schema, and target migration", List.of("inspect"),
                        List.of("AC-1", "AC-2", "AC-3"), PolicyAction.INSPECT_REPOSITORY),
                node("recovery-point", "Create and record a verified recovery point", List.of("assess-schema"),
                        List.of("AC-5"), PolicyAction.CREATE_RECOVERY_POINT),
                node("migration", "Apply versioned Flyway schema ownership", List.of("recovery-point"),
                        List.of("AC-1", "AC-2", "AC-3", "AC-4"), PolicyAction.MODIFY_DATABASE_SCHEMA),
                node("preservation-test", "Verify clean migration and existing-row preservation", List.of("assess-schema"),
                        allCriteria, PolicyAction.GENERATE_TESTS),
                node("validate", "Validate migration, ORM schema, recovery, and application tests",
                        List.of("migration", "preservation-test"), allCriteria, PolicyAction.RUN_LOCAL_TESTS));
        graphValidator.validate(graph);
        return graph;
    }

    private TaskNode node(String id, String name, List<String> dependencies,
                          List<String> criteria, PolicyAction action) {
        TaskStatus status = dependencies.isEmpty() ? TaskStatus.READY : TaskStatus.WAITING_FOR_DEPENDENCY;
        return new TaskNode(id, name, dependencies, criteria, action, status);
    }
}
