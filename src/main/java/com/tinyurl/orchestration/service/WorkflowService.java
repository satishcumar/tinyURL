package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.exception.PolicyViolationException;
import com.tinyurl.orchestration.exception.WorkflowNotFoundException;
import com.tinyurl.orchestration.exception.WorkflowStateException;
import com.tinyurl.orchestration.model.ApprovalRecord;
import com.tinyurl.orchestration.model.AuditEvent;
import com.tinyurl.orchestration.model.PolicyAction;
import com.tinyurl.orchestration.model.PolicyDecision;
import com.tinyurl.orchestration.model.RequirementAnalysis;
import com.tinyurl.orchestration.model.TaskNode;
import com.tinyurl.orchestration.model.WorkflowExecution;
import com.tinyurl.orchestration.model.WorkflowStatus;
import com.tinyurl.orchestration.model.ExecutionMetrics;
import com.tinyurl.orchestration.model.ReplanRecord;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorkflowService {
    private final Map<String, WorkflowExecution> executions = new ConcurrentHashMap<>();
    private final RequirementAnalyzer analyzer;
    private final WorkflowPlanner planner;
    private final PolicyEngine policyEngine;
    private final ArtifactStore artifactStore;
    private final WorkflowExecutionEngine executionEngine;
    private final UrlExpirationTaskRunner taskRunner;
    private final DependencyInvalidationService invalidationService;
    private final Clock clock;

    public WorkflowService(RequirementAnalyzer analyzer, WorkflowPlanner planner,
                           PolicyEngine policyEngine, ArtifactStore artifactStore,
                           WorkflowExecutionEngine executionEngine,
                           UrlExpirationTaskRunner taskRunner,
                           DependencyInvalidationService invalidationService, Clock clock) {
        this.analyzer = analyzer;
        this.planner = planner;
        this.policyEngine = policyEngine;
        this.artifactStore = artifactStore;
        this.executionEngine = executionEngine;
        this.taskRunner = taskRunner;
        this.invalidationService = invalidationService;
        this.clock = clock;
    }

    public WorkflowExecution create(String requirement) {
        requireAutomatic(PolicyAction.ANALYZE_REQUIREMENT);
        Instant now = clock.instant();
        String id = UUID.randomUUID().toString();
        RequirementAnalysis analysis = analyzer.analyze(requirement);

        requireAutomatic(PolicyAction.CREATE_PLAN);
        List<TaskNode> graph = planner.plan(analysis);
        WorkflowExecution execution = new WorkflowExecution(
                id, requirement, 1, WorkflowStatus.AWAITING_PLAN_APPROVAL,
                analysis, graph, List.of(), List.of(), ExecutionMetrics.notStarted(graph.size()),
                List.of(), null, null, now, now);
        executions.put(id, execution);
        artifactStore.saveSnapshot(execution);
        artifactStore.appendEvent(new AuditEvent(id, "PLAN_CREATED", "orchestrator", now,
                Map.of("taskCount", graph.size(), "approvalRequired", true)));
        return execution;
    }

    public WorkflowExecution get(String id) {
        WorkflowExecution current = executions.get(id);
        if (current != null) {
            return current;
        }
        WorkflowExecution restored = artifactStore.loadSnapshot(id)
                .orElseThrow(() -> new WorkflowNotFoundException(id));
        if (restored.attempts() == null || restored.rollbacks() == null ||
                restored.metrics() == null || restored.replans() == null ||
                restored.analysis().scenario() == null ||
                restored.analysis().repositoryImpacts() == null) {
            RequirementAnalysis defaults = analyzer.analyze(restored.requirement());
            RequirementAnalysis restoredAnalysis = new RequirementAnalysis(
                    restored.analysis().scenario() == null
                            ? defaults.scenario() : restored.analysis().scenario(),
                    restored.analysis().normalizedRequirement(),
                    restored.analysis().acceptanceCriteria(),
                    restored.analysis().assumptions(),
                    restored.analysis().ambiguities(),
                    restored.analysis().risks(),
                    restored.analysis().repositoryImpacts() == null
                            ? defaults.repositoryImpacts() : restored.analysis().repositoryImpacts());
            restored = new WorkflowExecution(restored.id(), restored.requirement(),
                    restored.requirementVersion(), restored.status(), restoredAnalysis,
                    restored.taskGraph(),
                    restored.attempts() == null ? List.of() : restored.attempts(),
                    restored.rollbacks() == null ? List.of() : restored.rollbacks(),
                    restored.metrics() == null
                            ? ExecutionMetrics.notStarted(restored.taskGraph().size())
                            : restored.metrics(),
                    restored.replans() == null ? List.of() : restored.replans(),
                    restored.planApproval(), restored.schemaApproval(),
                    restored.createdAt(), restored.updatedAt());
        }
        executions.put(id, restored);
        return restored;
    }

    public synchronized WorkflowExecution approvePlan(String id, String approvedBy, String rationale) {
        WorkflowExecution current = get(id);
        if (current.status() != WorkflowStatus.AWAITING_PLAN_APPROVAL) {
            throw new WorkflowStateException("Plan approval is not allowed from state " + current.status());
        }
        Instant now = clock.instant();
        ApprovalRecord approval = new ApprovalRecord(approvedBy, rationale, now);
        boolean schemaChange = current.taskGraph().stream()
                .anyMatch(task -> task.action() == PolicyAction.MODIFY_DATABASE_SCHEMA);
        WorkflowStatus nextStatus = schemaChange
                ? WorkflowStatus.AWAITING_SCHEMA_APPROVAL
                : WorkflowStatus.READY_FOR_EXECUTION;
        WorkflowExecution approved = current.approvePlan(approval, nextStatus, now);
        executions.put(id, approved);
        artifactStore.saveSnapshot(approved);
        artifactStore.appendEvent(new AuditEvent(id, "PLAN_APPROVED", approvedBy, now,
                Map.of("rationale", rationale, "previousState", current.status().name())));
        return approved;
    }

    public synchronized WorkflowExecution approveSchemaChange(
            String id, String approvedBy, String rationale) {
        WorkflowExecution current = get(id);
        if (current.status() != WorkflowStatus.AWAITING_SCHEMA_APPROVAL) {
            throw new WorkflowStateException(
                    "Schema approval is not allowed from state " + current.status());
        }
        Instant now = clock.instant();
        ApprovalRecord approval = new ApprovalRecord(approvedBy, rationale, now);
        WorkflowExecution approved = current.approveSchema(approval, now);
        executions.put(id, approved);
        artifactStore.saveSnapshot(approved);
        artifactStore.appendEvent(new AuditEvent(id, "SCHEMA_CHANGE_APPROVED", approvedBy, now,
                Map.of("rationale", rationale, "previousState", current.status().name())));
        return approved;
    }

    public List<String> artifacts(String id) {
        get(id);
        return artifactStore.listArtifacts(id);
    }

    public synchronized WorkflowExecution execute(String id) {
        WorkflowExecution current = get(id);
        if (current.status() != WorkflowStatus.READY_FOR_EXECUTION) {
            throw new WorkflowStateException("Execution is not allowed from state " + current.status());
        }
        Instant started = clock.instant();
        WorkflowExecution running = current.withExecution(WorkflowStatus.RUNNING,
                current.taskGraph(), current.attempts(), current.rollbacks(), current.metrics(), started);
        executions.put(id, running);
        artifactStore.saveSnapshot(running);
        artifactStore.appendEvent(new AuditEvent(id, "EXECUTION_STARTED", "orchestrator", started,
                Map.of("parallelismEnabled", true)));

        WorkflowExecution result = executionEngine.execute(running, taskRunner);
        executions.put(id, result);
        artifactStore.saveSnapshot(result);
        artifactStore.saveOutcomeArtifacts(result);
        artifactStore.appendEvent(new AuditEvent(id, "EXECUTION_FINISHED", "orchestrator",
                result.updatedAt(), Map.of(
                        "status", result.status().name(),
                        "successRate", result.metrics().successRate(),
                        "retryCount", result.metrics().retryCount(),
                        "rollbackCount", result.metrics().rollbackCount(),
                        "safeStopCount", result.metrics().safeStopCount())));
        return result;
    }

    public synchronized WorkflowExecution replan(String id, String requirement,
                                                  List<String> changedTaskIds, String rationale) {
        WorkflowExecution current = get(id);
        if (current.status() == WorkflowStatus.RUNNING || current.status() == WorkflowStatus.VALIDATING) {
            throw new WorkflowStateException("Replanning is not allowed while execution is active");
        }
        List<String> invalidated = invalidationService.invalidate(current.taskGraph(), changedTaskIds);
        RequirementAnalysis analysis = analyzer.analyze(requirement);
        List<TaskNode> graph = planner.plan(analysis);
        Instant now = clock.instant();
        ReplanRecord record = new ReplanRecord(current.requirementVersion(),
                current.requirementVersion() + 1, List.copyOf(changedTaskIds), invalidated,
                rationale, now);
        WorkflowExecution replanned = current.replan(requirement, analysis, graph, record, now);
        executions.put(id, replanned);
        artifactStore.saveSnapshot(replanned);
        artifactStore.appendEvent(new AuditEvent(id, "WORKFLOW_REPLANNED", "orchestrator", now,
                Map.of("fromVersion", current.requirementVersion(),
                        "toVersion", replanned.requirementVersion(),
                        "changedTasks", changedTaskIds,
                        "invalidatedTasks", invalidated,
                        "rationale", rationale)));
        return replanned;
    }

    private void requireAutomatic(PolicyAction action) {
        PolicyDecision decision = policyEngine.evaluate(action);
        if (decision != PolicyDecision.ALLOW) {
            throw new PolicyViolationException(action + " cannot run automatically: " + decision);
        }
    }
}
