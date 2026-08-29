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
    private final Clock clock;

    public WorkflowService(RequirementAnalyzer analyzer, WorkflowPlanner planner,
                           PolicyEngine policyEngine, ArtifactStore artifactStore, Clock clock) {
        this.analyzer = analyzer;
        this.planner = planner;
        this.policyEngine = policyEngine;
        this.artifactStore = artifactStore;
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
                analysis, graph, null, now, now);
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
        WorkflowExecution approved = current.approve(approval, current.taskGraph(), now);
        executions.put(id, approved);
        artifactStore.saveSnapshot(approved);
        artifactStore.appendEvent(new AuditEvent(id, "PLAN_APPROVED", approvedBy, now,
                Map.of("rationale", rationale, "previousState", current.status().name())));
        return approved;
    }

    public List<String> artifacts(String id) {
        get(id);
        return artifactStore.listArtifacts(id);
    }

    private void requireAutomatic(PolicyAction action) {
        PolicyDecision decision = policyEngine.evaluate(action);
        if (decision != PolicyDecision.ALLOW) {
            throw new PolicyViolationException(action + " cannot run automatically: " + decision);
        }
    }
}
