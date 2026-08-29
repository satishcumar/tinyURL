package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.ApprovalRecord;
import com.tinyurl.orchestration.model.ExecutionMetrics;
import com.tinyurl.orchestration.model.FailureClassification;
import com.tinyurl.orchestration.model.PolicyAction;
import com.tinyurl.orchestration.model.RequirementAnalysis;
import com.tinyurl.orchestration.model.RetryPolicy;
import com.tinyurl.orchestration.model.TaskNode;
import com.tinyurl.orchestration.model.TaskResult;
import com.tinyurl.orchestration.model.TaskStatus;
import com.tinyurl.orchestration.model.WorkflowExecution;
import com.tinyurl.orchestration.model.WorkflowStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowExecutionEngineTest {
    private final WorkflowExecutionEngine engine = new WorkflowExecutionEngine(
            new PolicyEngine(), Clock.systemUTC(), new RetryPolicy(3),
            Executors.newFixedThreadPool(3));

    @AfterEach
    void shutdown() {
        engine.shutdown();
    }

    @Test
    void retriesTransientFailureAndCompletes() {
        AtomicInteger implementationAttempts = new AtomicInteger();
        TaskRunner runner = (task, execution) -> {
            if (task.id().equals("implement") && implementationAttempts.incrementAndGet() < 3) {
                return TaskResult.failure(FailureClassification.TRANSIENT, "Temporary tool failure");
            }
            return TaskResult.success("done");
        };

        WorkflowExecution result = engine.execute(execution(graph()), runner);

        assertThat(result.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(result.metrics().retryCount()).isEqualTo(2);
        assertThat(result.metrics().successRate()).isEqualTo(1.0);
        assertThat(result.attempts()).filteredOn(attempt -> attempt.taskId().equals("implement"))
                .hasSize(3);
    }

    @Test
    void runsSiblingTasksInParallelBeforeSynchronizationNode() {
        CountDownLatch siblingsStarted = new CountDownLatch(2);
        var workers = ConcurrentHashMap.<String>newKeySet();
        TaskRunner runner = (task, execution) -> {
            if (task.id().equals("implement") || task.id().equals("test-design")) {
                workers.add(Thread.currentThread().getName());
                siblingsStarted.countDown();
                try {
                    if (!siblingsStarted.await(2, TimeUnit.SECONDS)) {
                        return TaskResult.failure(FailureClassification.VALIDATION,
                                "Sibling task did not start in parallel");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return TaskResult.failure(FailureClassification.PERMANENT, "Interrupted");
                }
            }
            return TaskResult.success("done");
        };

        WorkflowExecution result = engine.execute(execution(graph()), runner);

        assertThat(result.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(workers).hasSizeGreaterThanOrEqualTo(2);
        int validatePosition = taskCompletionPosition(result, "validate");
        assertThat(validatePosition).isGreaterThan(taskCompletionPosition(result, "implement"));
        assertThat(validatePosition).isGreaterThan(taskCompletionPosition(result, "test-design"));
    }

    @Test
    void safeStopsAndBlocksDownstreamTasksOnPermanentFailure() {
        TaskRunner runner = (task, execution) -> task.id().equals("implement")
                ? TaskResult.failure(FailureClassification.PERMANENT, "Unrecoverable change failure")
                : TaskResult.success("done");

        WorkflowExecution result = engine.execute(execution(graph()), runner);

        assertThat(result.status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(result.metrics().safeStopCount()).isEqualTo(1);
        assertThat(result.metrics().retryCount()).isZero();
        assertThat(result.metrics().rollbackCount()).isEqualTo(1);
        assertThat(result.rollbacks()).extracting("taskId").containsExactly("test-design");
        assertThat(result.taskGraph()).filteredOn(task -> task.id().equals("validate"))
                .extracting(TaskNode::status).containsExactly(TaskStatus.BLOCKED);
    }

    @Test
    void deniesProhibitedTaskEvenWithPlanApproval() {
        List<TaskNode> prohibited = List.of(new TaskNode("force", "Force push", List.of(),
                List.of("AC-1"), PolicyAction.FORCE_PUSH, TaskStatus.READY));

        WorkflowExecution result = engine.execute(execution(prohibited),
                (task, execution) -> TaskResult.success("should not run"));

        assertThat(result.status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(result.attempts()).anySatisfy(attempt -> {
            assertThat(attempt.failureClassification()).isEqualTo(FailureClassification.POLICY);
            assertThat(attempt.message()).contains("denied");
        });
    }

    private List<TaskNode> graph() {
        DependencyGraphValidator validator = new DependencyGraphValidator();
        RequirementAnalysis analysis = new RequirementAnalyzer().analyze("Add URL expiration");
        return new WorkflowPlanner(validator).plan(analysis);
    }

    private WorkflowExecution execution(List<TaskNode> graph) {
        Instant now = Instant.now();
        return new WorkflowExecution("11111111-1111-1111-1111-111111111111", "requirement", 1,
                WorkflowStatus.READY_FOR_EXECUTION,
                new RequirementAnalyzer().analyze("Add URL expiration"), graph, List.of(),
                List.of(),
                ExecutionMetrics.notStarted(graph.size()),
                new ApprovalRecord("reviewer", "approved", now), now, now);
    }

    private int taskCompletionPosition(WorkflowExecution result, String taskId) {
        for (int index = 0; index < result.attempts().size(); index++) {
            if (result.attempts().get(index).taskId().equals(taskId)) {
                return index;
            }
        }
        return -1;
    }
}
