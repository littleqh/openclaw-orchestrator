package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.entity.Worker;
import com.openclaw.orchestrator.repository.WorkerRepository;
import com.openclaw.orchestrator.service.LogStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Worker 日志流接口 - SSE 持续推送日志
 *
 * GET /api/workers/{id}/gateway/logs.tail/stream
 */
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class WorkerLogStreamController {

    private final LogStreamService logStreamService;
    private final WorkerRepository workerRepository;

    /**
     * 启动日志流 SSE 连接
     *
     * 连接建立后，每 2 秒推送一次新日志
     * 前端关闭页面时连接断开，停止推送
     */
    @GetMapping(value = "/{id}/gateway/logs.tail/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter logStream(@PathVariable Long id) {
        log.info("[SSE DEBUG] logStream called for worker: {}", id);

        // 验证 Worker 存在
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("[SSE DEBUG] Worker not found: {}", id);
                    return new RuntimeException("Worker not found: " + id);
                });

        log.info("[SSE DEBUG] Worker found: {}, gatewayUrl: {}", id, worker.getGatewayUrl());

        // 验证 gatewayUrl 配置
        if (worker.getGatewayUrl() == null || worker.getGatewayUrl().isBlank()) {
            log.error("[SSE DEBUG] Gateway URL not configured for worker: {}", id);
            throw new RuntimeException("Gateway URL not configured for worker: " + id);
        }

        log.info("[SSE DEBUG] Starting log stream for worker {}", id);
        return logStreamService.createLogStream(id);
    }

    /**
     * 测试端点 - 验证路由是否正确
     */
    @GetMapping("/{id}/gateway/logs.tail/stream/test")
    public String logStreamTest(@PathVariable Long id) {
        log.info("[SSE DEBUG] logStreamTest called for worker: {}", id);
        return "Test OK for worker " + id;
    }

    /**
     * 停止日志流
     */
    @DeleteMapping("/{id}/gateway/logs.tail/stream")
    public void stopLogStream(@PathVariable Long id) {
        log.info("Stopping log stream for worker {}", id);
        logStreamService.stopLogStream(id);
    }
}
