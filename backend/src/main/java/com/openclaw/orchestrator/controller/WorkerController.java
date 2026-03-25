package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.WorkerRequest;
import com.openclaw.orchestrator.entity.Worker;
import com.openclaw.orchestrator.service.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class WorkerController {

    private final WorkerService workerService;

    @GetMapping
    public ResponseEntity<List<Worker>> list() {
        return ResponseEntity.ok(workerService.listWorkers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Worker> get(@PathVariable Long id) {
        return ResponseEntity.ok(workerService.getWorker(id));
    }

    @PostMapping
    public ResponseEntity<Worker> create(@Valid @RequestBody WorkerRequest request) {
        return ResponseEntity.ok(workerService.createWorker(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Worker> update(@PathVariable Long id, @Valid @RequestBody WorkerRequest request) {
        return ResponseEntity.ok(workerService.updateWorker(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workerService.deleteWorker(id);
        return ResponseEntity.noContent().build();
    }
}
