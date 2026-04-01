package com.openclaw.orchestrator.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.openclaw.orchestrator.entity.Worker;
import com.openclaw.orchestrator.repository.WorkerRepository;
import com.openclaw.orchestrator.service.GatewayWsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Gateway 查询接口 - 通过 Worker 实体关联的 Gateway 进行查询
 *
 * API 格式: /api/workers/{workerId}/gateway/{method}
 */
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class WorkerGatewayController {

    private final WorkerRepository workerRepository;
    private final GatewayWsService gatewayWsService;

    /**
     * 通用 Gateway 调用 - 通过 WebSocket
     */
    private JsonNode invokeGateway(Worker worker, String method, Map<String, Object> params) {
        if (worker.getGatewayUrl() == null || worker.getGatewayUrl().isBlank()) {
            throw new RuntimeException("Gateway URL 未配置，请先在员工详情中配置 gatewayUrl");
        }

        try {
            return gatewayWsService.invokeTool(worker, method, params);
        } catch (Exception e) {
            log.error("Gateway invoke error: method={}, error={}", method, e.getMessage());
            return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        }
    }

    private Worker getWorker(Long workerId) {
        return workerRepository.findById(workerId)
                .orElseThrow(() -> new RuntimeException("Worker not found: " + workerId));
    }

    // ==================== 状态类接口 ====================

    /**
     * Gateway 运行时状态
     */
    @GetMapping("/{id}/gateway/status")
    public ResponseEntity<JsonNode> status(@PathVariable Long id) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "status", null));
    }

    /**
     * 健康检查
     */
    @GetMapping("/{id}/gateway/health")
    public ResponseEntity<JsonNode> health(@PathVariable Long id) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "health", null));
    }

    /**
     * 用量统计
     */
    @GetMapping("/{id}/gateway/usage")
    public ResponseEntity<JsonNode> usage(@PathVariable Long id) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "usage.status", null));
    }

    // ==================== 日志接口 ====================

    /**
     * 日志查询
     */
    @GetMapping("/{id}/gateway/logs.tail")
    public ResponseEntity<JsonNode> logsTail(
            @PathVariable Long id,
            @RequestParam(required = false) Integer cursor,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestParam(required = false) Integer maxBytes) {

        Worker worker = getWorker(id);
        var params = new java.util.HashMap<String, Object>();
        if (cursor != null) params.put("cursor", cursor);
        if (limit != null) params.put("limit", limit);
        if (maxBytes != null) params.put("maxBytes", maxBytes);

        return ResponseEntity.ok(invokeGateway(worker, "logs.tail", params.isEmpty() ? null : params));
    }

    // ==================== Agent / Session 接口 ====================

    /**
     * Agent 列表
     */
    @GetMapping("/{id}/gateway/agents.list")
    public ResponseEntity<JsonNode> agentsList(@PathVariable Long id) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "agents.list", null));
    }

    /**
     * Agent 文件列表
     */
    @GetMapping("/{id}/gateway/agents.files.list")
    public ResponseEntity<JsonNode> agentsFilesList(
            @PathVariable Long id,
            @RequestParam String agentId) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "agents.files.list", Map.of("agentId", agentId)));
    }

    /**
     * 获取 Agent 文件内容
     */
    @GetMapping("/{id}/gateway/agents.files.get")
    public ResponseEntity<JsonNode> agentsFilesGet(
            @PathVariable Long id,
            @RequestParam String agentId,
            @RequestParam String name) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "agents.files.get", Map.of("agentId", agentId, "name", name)));
    }

    /**
     * Session 列表
     */
    @PostMapping("/{id}/gateway/sessions.list")
    public ResponseEntity<JsonNode> sessionsList(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> params) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "sessions.list", params));
    }

    /**
     * Session 预览 - 获取 session 详细内容
     */
    @PostMapping("/{id}/gateway/sessions.preview")
    public ResponseEntity<JsonNode> sessionsPreview(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "sessions.preview", params));
    }

    /**
     * Session 历史消息 - 使用 sessions.preview 获取
     */
    @PostMapping("/{id}/gateway/sessions.history")
    public ResponseEntity<JsonNode> sessionsHistory(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "sessions.preview", params));
    }

    /**
     * 发送消息到 Session (通过 WebSocket)
     */
    @PostMapping("/{id}/gateway/sessions.send")
    public ResponseEntity<JsonNode> sessionsSend(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params) {
        Worker worker = getWorker(id);
        // 尝试通过 WebSocket 调用 sessions.send
        return ResponseEntity.ok(gatewayWsService.invokeTool(worker, "sessions.send", params));
    }

    // ==================== Skills 接口 ====================

    /**
     * Skills 状态
     */
    @GetMapping("/{id}/gateway/skills.status")
    public ResponseEntity<JsonNode> skillsStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String agentId) {
        Worker worker = getWorker(id);
        Map<String, Object> params = agentId != null ? Map.of("agentId", (Object) agentId) : null;
        return ResponseEntity.ok(invokeGateway(worker, "skills.status", params));
    }

    /**
     * Skills 可执行文件列表
     */
    @GetMapping("/{id}/gateway/skills.bins")
    public ResponseEntity<JsonNode> skillsBins(@PathVariable Long id) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "skills.bins", null));
    }

    // ==================== Config 接口 ====================

    /**
     * 获取当前配置
     */
    @GetMapping("/{id}/gateway/config.get")
    public ResponseEntity<JsonNode> configGet(@PathVariable Long id) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "config.get", null));
    }

    /**
     * 合并补丁到配置
     */
    @PatchMapping("/{id}/gateway/config.patch")
    public ResponseEntity<JsonNode> configPatch(
            @PathVariable Long id,
            @RequestBody Map<String, Object> patch) {
        Worker worker = getWorker(id);
        return ResponseEntity.ok(invokeGateway(worker, "config.patch", patch));
    }
}
