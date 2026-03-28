package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.workflow.*;
import com.openclaw.orchestrator.entity.OutputSchema;
import com.openclaw.orchestrator.service.BusinessWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/workflows")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class BusinessWorkflowController {

    private final BusinessWorkflowService workflowService;

    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> listWorkflows() {
        return ResponseEntity.ok(workflowService.listWorkflows());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable Long id) {
        return ResponseEntity.ok(workflowService.getWorkflow(id));
    }

    @PostMapping
    public ResponseEntity<WorkflowResponse> createWorkflow(
            @RequestBody WorkflowRequest request,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(workflowService.createWorkflow(request, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowResponse> updateWorkflow(
            @PathVariable Long id,
            @RequestBody WorkflowRequest request) {
        return ResponseEntity.ok(workflowService.updateWorkflow(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable Long id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/schemas")
    public ResponseEntity<List<OutputSchema>> listOutputSchemas() {
        return ResponseEntity.ok(workflowService.listOutputSchemas());
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null) return null;
        // Assume authentication.getName() returns user ID
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
