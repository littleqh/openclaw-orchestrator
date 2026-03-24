package com.openclaw.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.orchestrator.entity.GatewayInstance;
import com.openclaw.orchestrator.repository.GatewayInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayService {

    private final GatewayInstanceRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 实例管理 ──────────────────────────────────────

    public List<GatewayInstance> listInstances() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public GatewayInstance createInstance(GatewayInstance instance) {
        return repository.save(instance);
    }

    public void deleteInstance(Long id) {
        repository.deleteById(id);
    }

    // ── OpenClaw Gateway 调用 ────────────────────────────────

    /**
     * 调用 OpenClaw Gateway 的工具
     *
     * @param instance Gateway 实例
     * @param tool     工具名，如 "session_status", "sessions_list"
     * @param args     请求参数
     * @return Gateway 返回的 JSON
     */
    public JsonNode invokeTool(GatewayInstance instance, String tool, Map<String, Object> args) {
        WebClient client = WebClient.builder()
                .baseUrl(instance.getUrl().replaceFirst("/$", ""))
                .defaultHeader("Authorization", "Bearer " + instance.getToken())
                .defaultHeader("Content-Type", "application/json")
                .build();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("tool", tool);
        requestBody.put("action", "json");
        requestBody.put("args", args != null ? args : Collections.emptyMap());

        try {
            String response = client.post()
                    .uri("/tools/invoke")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .onErrorResume(e -> {
                        log.error("调用 Gateway 失败: {}", e.getMessage());
                        return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                    })
                    .block();

            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("解析 Gateway 响应失败", e);
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 获取实例状态
     */
    public JsonNode getStatus(GatewayInstance instance) {
        return invokeTool(instance, "session_status",
                Map.of("sessionKey", "agent:main:main"));
    }

    /**
     * 获取 Session 列表
     */
    public JsonNode getSessions(GatewayInstance instance) {
        return invokeTool(instance, "sessions_list", Collections.emptyMap());
    }

    /**
     * 获取子 Agent 列表
     */
    public JsonNode getSubagents(GatewayInstance instance) {
        return invokeTool(instance, "subagents", Map.of("action", "list"));
    }

    /**
     * 获取记忆搜索
     */
    public JsonNode searchMemory(GatewayInstance instance, String query, int maxResults) {
        return invokeTool(instance, "memory_search",
                Map.of("query", query, "maxResults", maxResults));
    }
}
