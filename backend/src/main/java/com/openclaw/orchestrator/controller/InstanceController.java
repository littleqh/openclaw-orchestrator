package com.openclaw.orchestrator.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.openclaw.orchestrator.dto.InstanceRequest;
import com.openclaw.orchestrator.entity.GatewayInstance;
import com.openclaw.orchestrator.service.GatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/instances")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class InstanceController {

    private final GatewayService gatewayService;

    // ── CRUD ──────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<GatewayInstance>> list() {
        return ResponseEntity.ok(gatewayService.listInstances());
    }

    @PostMapping
    public ResponseEntity<GatewayInstance> create(@Valid @RequestBody InstanceRequest req) {
        GatewayInstance instance = GatewayInstance.builder()
                .name(req.getName())
                .url(req.getUrl())
                .token(req.getToken())
                .description(req.getDescription())
                .build();
        return ResponseEntity.ok(gatewayService.createInstance(instance));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        gatewayService.deleteInstance(id);
        return ResponseEntity.noContent().build();
    }

    // ── Gateway 工具调用 ─────────────────────────────────

    @GetMapping("/{id}/status")
    public ResponseEntity<JsonNode> status(@PathVariable Long id) {
        GatewayInstance instance = gatewayService.listInstances().stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("实例不存在"));
        return ResponseEntity.ok(gatewayService.getStatus(instance));
    }

    @GetMapping("/{id}/sessions")
    public ResponseEntity<JsonNode> sessions(@PathVariable Long id) {
        GatewayInstance instance = gatewayService.listInstances().stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("实例不存在"));
        return ResponseEntity.ok(gatewayService.getSessions(instance));
    }

    @GetMapping("/{id}/subagents")
    public ResponseEntity<JsonNode> subagents(@PathVariable Long id) {
        GatewayInstance instance = gatewayService.listInstances().stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("实例不存在"));
        return ResponseEntity.ok(gatewayService.getSubagents(instance));
    }

    @GetMapping("/{id}/memory")
    public ResponseEntity<JsonNode> memory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "任务") String query,
            @RequestParam(defaultValue = "5") int maxResults) {
        GatewayInstance instance = gatewayService.listInstances().stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("实例不存在"));
        return ResponseEntity.ok(gatewayService.searchMemory(instance, query, maxResults));
    }

    // ── 通用工具调用 ────────────────────────────────────────

    @PostMapping("/{id}/invoke")
    public ResponseEntity<JsonNode> invoke(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        GatewayInstance instance = gatewayService.listInstances().stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("实例不存在"));

        String tool = (String) request.get("tool");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) request.getOrDefault("args", java.util.Collections.emptyMap());

        return ResponseEntity.ok(gatewayService.invokeTool(instance, tool, args));
    }
}
