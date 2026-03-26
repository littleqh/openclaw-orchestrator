package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.OperationDetail;
import com.openclaw.orchestrator.dto.OperationRequest;
import com.openclaw.orchestrator.service.OperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class OperationController {

    private final OperationService operationService;

    @GetMapping
    public List<OperationDetail> list() {
        return operationService.list();
    }

    @GetMapping("/{id}")
    public OperationDetail get(@PathVariable Long id) {
        return operationService.getById(id);
    }

    @PostMapping
    public OperationDetail create(@Valid @RequestBody OperationRequest request) {
        return operationService.create(request);
    }

    @PutMapping("/{id}")
    public OperationDetail update(@PathVariable Long id, @Valid @RequestBody OperationRequest request) {
        return operationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        operationService.delete(id);
    }
}
