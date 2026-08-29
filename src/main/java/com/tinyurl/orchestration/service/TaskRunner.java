package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.TaskNode;
import com.tinyurl.orchestration.model.TaskResult;
import com.tinyurl.orchestration.model.WorkflowExecution;

@FunctionalInterface
public interface TaskRunner {
    TaskResult run(TaskNode task, WorkflowExecution execution);

    default TaskResult rollback(TaskNode task, WorkflowExecution execution) {
        return TaskResult.success("No rollback action required");
    }
}
