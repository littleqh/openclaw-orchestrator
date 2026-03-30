package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.LLMModelDetail;
import com.openclaw.orchestrator.dto.LLMModelRequest;
import com.openclaw.orchestrator.dto.LLMModelTestResponse;
import com.openclaw.orchestrator.service.LLMModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/llm-models")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class LLMModelController {

    private final LLMModelService llmModelService;

    @GetMapping
    public List<LLMModelDetail> list() {
        return llmModelService.list();
    }

    @GetMapping("/enabled")
    public List<LLMModelDetail> listEnabled() {
        return llmModelService.listEnabled();
    }

    @GetMapping("/{id}")
    public LLMModelDetail get(@PathVariable Long id) {
        return llmModelService.getById(id);
    }

    @PostMapping
    public LLMModelDetail create(@Valid @RequestBody LLMModelRequest request) {
        return llmModelService.create(request);
    }

    @PutMapping("/{id}")
    public LLMModelDetail update(@PathVariable Long id, @Valid @RequestBody LLMModelRequest request) {
        return llmModelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        llmModelService.delete(id);
    }

    @PostMapping("/{id}/test")
    public LLMModelTestResponse testConnection(@PathVariable Long id) {
        return llmModelService.testConnection(id);
    }
}
