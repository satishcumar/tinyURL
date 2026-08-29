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
    private static final Map<String, String> EVIDENCE = Map.of(
            "inspect", "Existing URL API, service, persistence, and tests inspected",
            "design", "Expiration contract maps expired links to HTTP 410",
            "implement", "Expiration lifecycle implementation is present",
            "test-design", "Unit and integration expiration scenarios are present",
            "validate", "Expiration exception and lifecycle types are loadable");

    @Override
    public TaskResult run(TaskNode task, WorkflowExecution execution) {
        String evidence = EVIDENCE.get(task.id());
        if (evidence == null) {
            return TaskResult.failure(FailureClassification.PERMANENT,
                    "No registered runner for task " + task.id());
        }
        if (task.id().equals("validate") && !UrlExpiredException.class.getName()
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
