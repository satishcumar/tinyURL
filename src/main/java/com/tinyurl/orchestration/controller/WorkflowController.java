package com.tinyurl.orchestration.controller;

import com.tinyurl.orchestration.dto.ApprovePlanRequest;
import com.tinyurl.orchestration.dto.CreateWorkflowRequest;
import com.tinyurl.orchestration.dto.RecordCommandRequest;
import com.tinyurl.orchestration.model.WorkflowExecution;
import com.tinyurl.orchestration.service.CommandAuditService;
import com.tinyurl.orchestration.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowService workflowService;
    private final CommandAuditService commandAuditService;

    public WorkflowController(WorkflowService workflowService, CommandAuditService commandAuditService) {
        this.workflowService = workflowService;
        this.commandAuditService = commandAuditService;
    }

    @PostMapping
    public ResponseEntity<WorkflowExecution> create(@Valid @RequestBody CreateWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.create(request.requirement()));
    }

    @GetMapping("/{id}")
    public WorkflowExecution get(@PathVariable String id) {
        return workflowService.get(id);
    }

    @PostMapping("/{id}/plan-approval")
    public WorkflowExecution approvePlan(@PathVariable String id,
                                         @Valid @RequestBody ApprovePlanRequest request) {
        return workflowService.approvePlan(id, request.approvedBy(), request.rationale());
    }

    @PostMapping("/{id}/commands")
    public ResponseEntity<Void> recordCommand(@PathVariable String id,
                                              @Valid @RequestBody RecordCommandRequest request) {
        commandAuditService.record(id, request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/artifacts")
    public List<String> artifacts(@PathVariable String id) {
        return workflowService.artifacts(id);
    }
}
