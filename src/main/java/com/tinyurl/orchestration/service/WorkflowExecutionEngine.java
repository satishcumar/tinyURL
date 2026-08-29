package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.ExecutionMetrics;
import com.tinyurl.orchestration.model.FailureClassification;
import com.tinyurl.orchestration.model.PolicyDecision;
import com.tinyurl.orchestration.model.RetryPolicy;
import com.tinyurl.orchestration.model.RollbackRecord;
import com.tinyurl.orchestration.model.TaskAttempt;
import com.tinyurl.orchestration.model.TaskNode;
import com.tinyurl.orchestration.model.TaskResult;
import com.tinyurl.orchestration.model.TaskStatus;
import com.tinyurl.orchestration.model.WorkflowExecution;
import com.tinyurl.orchestration.model.WorkflowStatus;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class WorkflowExecutionEngine {
    private final PolicyEngine policyEngine;
    private final Clock clock;
    private final RetryPolicy retryPolicy;
    private final ExecutorService executor;

    @Autowired
    public WorkflowExecutionEngine(PolicyEngine policyEngine, Clock clock,
                                   @Value("${orchestration.max-attempts:3}") int maxAttempts,
                                   @Value("${orchestration.parallelism:3}") int parallelism) {
        this(policyEngine, clock, new RetryPolicy(maxAttempts),
                Executors.newFixedThreadPool(parallelism));
    }

    WorkflowExecutionEngine(PolicyEngine policyEngine, Clock clock,
                            RetryPolicy retryPolicy, ExecutorService executor) {
        this.policyEngine = policyEngine;
        this.clock = clock;
        this.retryPolicy = retryPolicy;
        this.executor = executor;
    }

    public WorkflowExecution execute(WorkflowExecution execution, TaskRunner runner) {
        Instant startedAt = clock.instant();
        List<TaskNode> graph = new ArrayList<>(execution.taskGraph());
        List<TaskAttempt> attempts = new ArrayList<>(execution.attempts());
        List<RollbackRecord> rollbacks = new ArrayList<>(execution.rollbacks());

        while (graph.stream().anyMatch(task -> task.status() != TaskStatus.SUCCEEDED)) {
            promoteReadyTasks(graph);
            List<Integer> readyIndexes = readyIndexes(graph);
            if (readyIndexes.isEmpty()) {
                return safeStop(execution, graph, attempts, rollbacks, startedAt,
                        "No runnable tasks remain; dependencies cannot be satisfied", runner);
            }

            List<CompletableFuture<TaskRunOutcome>> futures = new ArrayList<>();
            for (int index : readyIndexes) {
                TaskNode running = graph.get(index).withStatus(TaskStatus.RUNNING);
                graph.set(index, running);
                futures.add(CompletableFuture.supplyAsync(
                        () -> runWithRetry(running, execution, runner), executor));
            }

            boolean failed = false;
            for (int offset = 0; offset < futures.size(); offset++) {
                TaskRunOutcome outcome = futures.get(offset).join();
                int graphIndex = readyIndexes.get(offset);
                attempts.addAll(outcome.attempts());
                graph.set(graphIndex, graph.get(graphIndex).withStatus(
                        outcome.successful() ? TaskStatus.SUCCEEDED : TaskStatus.FAILED));
                failed |= !outcome.successful();
            }
            if (failed) {
                return safeStop(execution, graph, attempts, rollbacks, startedAt,
                        "A task exhausted its authorized retry policy", runner);
            }
        }

        Instant completedAt = clock.instant();
        ExecutionMetrics metrics = metrics(graph, attempts, rollbacks, startedAt, completedAt, false);
        return execution.withExecution(WorkflowStatus.COMPLETED, List.copyOf(graph),
                List.copyOf(attempts), List.copyOf(rollbacks), metrics, completedAt);
    }

    private TaskRunOutcome runWithRetry(TaskNode task, WorkflowExecution execution, TaskRunner runner) {
        List<TaskAttempt> attempts = new ArrayList<>();
        for (int attemptNumber = 1; attemptNumber <= retryPolicy.maxAttempts(); attemptNumber++) {
            Instant started = clock.instant();
            TaskResult result = policyResult(task, execution);
            if (result == null) {
                try {
                    result = runner.run(task, execution);
                } catch (RuntimeException exception) {
                    result = TaskResult.failure(FailureClassification.PERMANENT,
                            "Task runner failed: " + exception.getClass().getSimpleName());
                }
            }
            Instant completed = clock.instant();
            attempts.add(new TaskAttempt(task.id(), attemptNumber, result.successful(),
                    result.failureClassification(), result.message(), started, completed,
                    Thread.currentThread().getName()));
            if (result.successful()) {
                return new TaskRunOutcome(true, attempts);
            }
            if (result.failureClassification() != FailureClassification.TRANSIENT) {
                break;
            }
        }
        return new TaskRunOutcome(false, attempts);
    }

    private TaskResult policyResult(TaskNode task, WorkflowExecution execution) {
        PolicyDecision decision = policyEngine.evaluate(task.action());
        if (decision == PolicyDecision.DENY) {
            return TaskResult.failure(FailureClassification.POLICY,
                    "Policy denied action " + task.action());
        }
        if (task.action() == com.tinyurl.orchestration.model.PolicyAction.MODIFY_DATABASE_SCHEMA &&
                execution.schemaApproval() == null) {
            return TaskResult.failure(FailureClassification.POLICY,
                    "Schema change requires explicit approval");
        }
        if (decision == PolicyDecision.REQUIRE_APPROVAL && execution.planApproval() == null) {
            return TaskResult.failure(FailureClassification.POLICY,
                    "Action requires an approved plan: " + task.action());
        }
        return null;
    }

    private void promoteReadyTasks(List<TaskNode> graph) {
        Set<String> succeeded = new HashSet<>();
        graph.stream().filter(task -> task.status() == TaskStatus.SUCCEEDED)
                .map(TaskNode::id).forEach(succeeded::add);
        for (int index = 0; index < graph.size(); index++) {
            TaskNode task = graph.get(index);
            if ((task.status() == TaskStatus.PENDING ||
                    task.status() == TaskStatus.WAITING_FOR_DEPENDENCY) &&
                    succeeded.containsAll(task.dependsOn())) {
                graph.set(index, task.withStatus(TaskStatus.READY));
            }
        }
    }

    private List<Integer> readyIndexes(List<TaskNode> graph) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < graph.size(); index++) {
            if (graph.get(index).status() == TaskStatus.READY) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private WorkflowExecution safeStop(WorkflowExecution execution, List<TaskNode> graph,
                                       List<TaskAttempt> attempts, List<RollbackRecord> rollbacks,
                                       Instant startedAt, String reason, TaskRunner runner) {
        rollbackSucceededChanges(execution, graph, rollbacks, runner);
        for (int index = 0; index < graph.size(); index++) {
            TaskNode task = graph.get(index);
            if (task.status() != TaskStatus.SUCCEEDED && task.status() != TaskStatus.FAILED) {
                graph.set(index, task.withStatus(TaskStatus.BLOCKED));
            }
        }
        Instant completedAt = clock.instant();
        List<TaskAttempt> recorded = new ArrayList<>(attempts);
        recorded.add(new TaskAttempt("orchestrator", 1, false,
                FailureClassification.PERMANENT, reason, completedAt, completedAt,
                Thread.currentThread().getName()));
        ExecutionMetrics metrics = metrics(graph, recorded, rollbacks, startedAt, completedAt, true);
        return execution.withExecution(WorkflowStatus.SAFE_STOPPED, List.copyOf(graph),
                List.copyOf(recorded), List.copyOf(rollbacks), metrics, completedAt);
    }

    private void rollbackSucceededChanges(WorkflowExecution execution, List<TaskNode> graph,
                                          List<RollbackRecord> rollbacks, TaskRunner runner) {
        for (int index = graph.size() - 1; index >= 0; index--) {
            TaskNode task = graph.get(index);
            if (task.status() != TaskStatus.SUCCEEDED || !isReversible(task)) {
                continue;
            }
            Instant started = clock.instant();
            TaskResult result;
            try {
                result = runner.rollback(task, execution);
            } catch (RuntimeException exception) {
                result = TaskResult.failure(FailureClassification.PERMANENT,
                        "Rollback failed: " + exception.getClass().getSimpleName());
            }
            rollbacks.add(new RollbackRecord(task.id(), result.successful(), result.message(),
                    started, clock.instant()));
        }
    }

    private boolean isReversible(TaskNode task) {
        return task.action() == com.tinyurl.orchestration.model.PolicyAction.EDIT_FEATURE_CODE ||
                task.action() == com.tinyurl.orchestration.model.PolicyAction.GENERATE_TESTS;
    }

    private ExecutionMetrics metrics(List<TaskNode> graph, List<TaskAttempt> attempts,
                                     List<RollbackRecord> rollbacks,
                                     Instant startedAt, Instant completedAt, boolean safeStopped) {
        int succeeded = (int) graph.stream().filter(task -> task.status() == TaskStatus.SUCCEEDED).count();
        int failed = (int) graph.stream().filter(task -> task.status() == TaskStatus.FAILED).count();
        long taskAttempts = attempts.stream().filter(attempt -> !attempt.taskId().equals("orchestrator")).count();
        long attemptedTasks = attempts.stream().filter(attempt -> !attempt.taskId().equals("orchestrator"))
                .map(TaskAttempt::taskId).distinct().count();
        int retries = Math.toIntExact(taskAttempts - attemptedTasks);
        double successRate = graph.isEmpty() ? 0.0 : (double) succeeded / graph.size();
        double retryFrequency = attemptedTasks == 0 ? 0.0 : (double) retries / attemptedTasks;
        double rollbackFrequency = attemptedTasks == 0 ? 0.0 : (double) rollbacks.size() / attemptedTasks;
        long meanRepairTime = meanRepairTime(attempts);
        return new ExecutionMetrics(graph.size(), succeeded, failed, retries, rollbacks.size(),
                safeStopped ? 1 : 0, successRate, retryFrequency, rollbackFrequency, meanRepairTime,
                Duration.between(startedAt, completedAt).toMillis(), startedAt, completedAt);
    }

    private long meanRepairTime(List<TaskAttempt> attempts) {
        List<Long> repairTimes = new ArrayList<>();
        for (String taskId : attempts.stream().map(TaskAttempt::taskId).distinct().toList()) {
            List<TaskAttempt> taskAttempts = attempts.stream()
                    .filter(attempt -> attempt.taskId().equals(taskId)).toList();
            if (taskAttempts.size() > 1 && taskAttempts.get(taskAttempts.size() - 1).successful()) {
                repairTimes.add(Duration.between(taskAttempts.get(0).startedAt(),
                        taskAttempts.get(taskAttempts.size() - 1).completedAt()).toMillis());
            }
        }
        return repairTimes.isEmpty() ? 0L :
                Math.round(repairTimes.stream().mapToLong(Long::longValue).average().orElse(0.0));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private record TaskRunOutcome(boolean successful, List<TaskAttempt> attempts) {
    }
}
