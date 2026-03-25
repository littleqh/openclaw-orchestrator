package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.WorkflowDetail;
import com.openclaw.orchestrator.dto.WorkflowRequest;
import com.openclaw.orchestrator.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping
    public List<WorkflowDetail> list() {
        return workflowService.list();
    }

    @GetMapping("/{id}")
    public WorkflowDetail get(@PathVariable Long id) {
        return workflowService.getById(id)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:设计不存在"));
    }

    @PostMapping
    public WorkflowDetail create(@Valid @RequestBody WorkflowRequest request) {
        return workflowService.create(request);
    }

    @PutMapping("/{id}")
    public WorkflowDetail update(@PathVariable Long id, @Valid @RequestBody WorkflowRequest request) {
        return workflowService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        workflowService.delete(id);
    }
}
