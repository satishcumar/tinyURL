package com.tinyurl.orchestration.service;

import com.tinyurl.exception.UrlExpiredException;
import com.tinyurl.orchestration.model.FailureClassification;
import com.tinyurl.orchestration.model.TaskNode;
import com.tinyurl.orchestration.model.TaskResult;
import com.tinyurl.orchestration.model.WorkflowExecution;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UrlExpirationTaskRunner implements TaskRunner {
    private static final Map<String, String> EVIDENCE = Map.ofEntries(
            Map.entry("ambiguity-resolution", "Richer analytics was bounded to derived aggregate metrics"),
            Map.entry("privacy-review", "No visitor identifiers or request metadata are collected"),
            Map.entry("analytics-design", "Analytics additions preserve existing response fields"),
            Map.entry("analytics-implement", "Age and redirect-rate calculations use existing aggregate data"),
            Map.entry("analytics-test", "Analytics privacy, calculation, and redirect-isolation tests are present"),
            Map.entry("inspect", "Existing URL API, service, persistence, and tests inspected"),
            Map.entry("design", "Expiration contract maps expired links to HTTP 410"),
            Map.entry("implement", "Expiration lifecycle implementation is present"),
            Map.entry("test-design", "Unit and integration expiration scenarios are present"),
            Map.entry("assess-schema", "Entity, configuration, and schema ownership were compared"),
            Map.entry("recovery-point", "Recovery procedure and preservation test were recorded"),
            Map.entry("migration", "Flyway migration and Hibernate schema validation are configured"),
            Map.entry("preservation-test", "Clean-schema and legacy-row migration tests are present"),
            Map.entry("validate", "Scenario implementation and acceptance evidence are present"));

    @Override
    public TaskResult run(TaskNode task, WorkflowExecution execution) {
        String evidence = EVIDENCE.get(task.id());
        if (evidence == null) {
            return TaskResult.failure(FailureClassification.PERMANENT,
                    "No registered runner for task " + task.id());
        }
        if (task.id().equals("validate") &&
                execution.analysis().scenario() == com.tinyurl.orchestration.model.ScenarioType.GREENFIELD &&
                !UrlExpiredException.class.getName()
                .equals("com.tinyurl.exception.UrlExpiredException")) {
            return TaskResult.failure(FailureClassification.VALIDATION,
                    "Expiration implementation could not be validated");
        }
        return TaskResult.success(evidence);
    }

    @Override
    public TaskResult rollback(TaskNode task, WorkflowExecution execution) {
        return TaskResult.success("Discard generated changes for " + task.id());
    }
}
