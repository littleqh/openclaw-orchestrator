package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.StatusData;
import com.openclaw.orchestrator.entity.GatewayInstance;
import com.openclaw.orchestrator.service.GatewayService;
import com.openclaw.orchestrator.service.MonitorService;
import com.openclaw.orchestrator.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class SseStatusController {

    private final SseEmitterService sseEmitterService;
    private final MonitorService monitorService;
    private final GatewayService gatewayService;

    /**
     * SSE endpoint for real-time status updates.
     * GET /api/sse/status/{instanceId}
     */
    @GetMapping(value = "/status/{instanceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long instanceId) {
        log.info("[SSE] Received subscription request for instance {}", instanceId);

        // 1. 验证实例存在
        Optional<GatewayInstance> instance = gatewayService.listInstances().stream()
                .filter(i -> i.getId().equals(instanceId))
                .findFirst();
        if (instance.isEmpty()) {
            log.warn("Instance {} not found, rejecting SSE connection", instanceId);
            throw new RuntimeException("实例不存在: " + instanceId);
        }

        // 2. 创建 SSE 连接
        SseEmitter emitter = sseEmitterService.subscribe(instanceId);

        // 3. 立即推送缓存的最新状态（如果有）
        // 注意：如果发送失败，emitter 的错误处理会自动移除它
        Optional<StatusData> cached = monitorService.getCachedStatus(instanceId);
        if (cached.isPresent()) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(cached.get()));
            } catch (Exception e) {
                log.debug("Failed to send cached status on connect (emitter likely dead): {}", e.getMessage());
                // emitter 已经在 sseEmitterService 的 onError 中被移除
            }
        }

        return emitter;
    }
}
