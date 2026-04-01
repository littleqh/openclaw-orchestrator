package com.openclaw.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.orchestrator.entity.Worker;
import com.openclaw.orchestrator.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 日志流服务 - 管理 Worker 的日志 SSE 连接
 *
 * 每个 SSE 连接对应一个定时任务，定期调用 logs.tail 获取新日志并推送
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogStreamService {

    private final WorkerRepository workerRepository;
    private final GatewayWsService gatewayWsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** SSE 超时时间 - 10 分钟 */
    private static final long SSE_TIMEOUT = 10 * 60 * 1000L;

    /** 日志轮询间隔 - 2 秒 */
    private static final long POLL_INTERVAL_MS = 2000L;

    /** Worker ID -> Log Stream Context */
    private final Map<Long, LogStreamContext> logStreams = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * 创建日志流 SSE 连接
     */
    public SseEmitter createLogStream(Long workerId) {
        log.info("[LogStreamService] Creating log stream for worker: {}", workerId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        LogStreamContext context = new LogStreamContext(emitter, workerId);

        logStreams.put(workerId, context);

        emitter.onCompletion(() -> {
            log.info("[LogStreamService] Log stream completed for worker {}", workerId);
            stopLogStream(workerId);
        });
        emitter.onTimeout(() -> {
            log.info("[LogStreamService] Log stream timeout for worker {}", workerId);
            stopLogStream(workerId);
        });
        emitter.onError(e -> {
            log.info("[LogStreamService] Log stream error for worker {}: {}", workerId, e.getMessage());
            stopLogStream(workerId);
        });

        // 立即发送一次初始日志
        sendLogs(workerId);

        // 启动定时轮询
        startLogPolling(workerId);

        return emitter;
    }

    /**
     * 启动日志轮询
     */
    private void startLogPolling(Long workerId) {
        LogStreamContext context = logStreams.get(workerId);
        if (context == null) return;

        context.task = scheduler.scheduleAtFixedRate(() -> {
            if (!context.isActive()) {
                stopLogStream(workerId);
                return;
            }
            sendLogs(workerId);
        }, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止日志流
     */
    public void stopLogStream(Long workerId) {
        LogStreamContext context = logStreams.remove(workerId);
        if (context != null) {
            context.stop();
        }
        log.info("Stopped log stream for worker {}, remaining: {}", workerId, logStreams.size());
    }

    /**
     * 发送日志到 SSE
     */
    private void sendLogs(Long workerId) {
        LogStreamContext context = logStreams.get(workerId);
        if (context == null || !context.isActive()) {
            log.debug("[LogStreamService] sendLogs - context null or inactive");
            return;
        }

        try {
            Worker worker = workerRepository.findById(workerId).orElse(null);
            if (worker == null) {
                log.warn("[LogStreamService] sendLogs - Worker not found: {}", workerId);
                return;
            }

            // 获取当前的 cursor（首次为 null）
            Integer cursor = context.cursor;

            JsonNode response = fetchLogs(worker, cursor);
            LogResult result = extractLogContentWithCursor(response, context);

            // 更新 cursor
            if (result.newCursor != null) {
                context.cursor = result.newCursor;
            }

            if (result.content != null && !result.content.isEmpty()) {
                context.send(result.content);
            }
        } catch (Exception e) {
            log.error("[LogStreamService] Error fetching logs for worker {}: {}", workerId, e.getMessage());
        }
    }

    /**
     * 通过 WebSocket 调用 Gateway logs.tail
     */
    private JsonNode fetchLogs(Worker worker, Integer cursor) {
        try {
            Map<String, Object> params = new java.util.HashMap<>();
            params.put("limit", 100);
            if (cursor != null) {
                params.put("cursor", cursor);
            }
            return gatewayWsService.invokeTool(worker, "logs.tail", params);
        } catch (Exception e) {
            log.error("[LogStreamService] Failed to fetch logs: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 日志提取结果
     */
    private static class LogResult {
        String content;
        Integer newCursor;

        LogResult(String content, Integer newCursor) {
            this.content = content;
            this.newCursor = newCursor;
        }
    }

    /**
     * 从响应中提取日志内容和新的 cursor
     */
    private LogResult extractLogContentWithCursor(JsonNode response, LogStreamContext context) {
        try {
            if (response == null) {
                return new LogResult(null, null);
            }
            if (!response.has("ok") || !response.get("ok").asBoolean()) {
                return new LogResult(null, null);
            }

            // Gateway 返回格式: {"type":"res","ok":true,"payload":{"cursor":8534,"lines":[...]}}
            JsonNode payload = response.path("payload");
            if (payload.isMissingNode()) {
                return new LogResult(null, null);
            }

            // 提取 cursor
            Integer newCursor = payload.has("cursor") ? payload.get("cursor").asInt() : null;

            // 如果 cursor 没变，说明没有新日志
            if (newCursor != null && newCursor.equals(context.cursor)) {
                return new LogResult(null, newCursor);
            }

            // 日志在 payload.lines 数组里
            JsonNode linesNode = payload.path("lines");
            if (linesNode.isMissingNode() || !linesNode.isArray()) {
                return new LogResult(null, newCursor);
            }

            if (linesNode.size() == 0) {
                return new LogResult(null, newCursor);
            }

            // 解析每行日志，提取消息内容
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < linesNode.size(); i++) {
                try {
                    JsonNode lineElement = linesNode.get(i);

                    JsonNode lineNode;
                    if (lineElement.isTextual()) {
                        lineNode = objectMapper.readTree(lineElement.asText());
                    } else if (lineElement.isObject()) {
                        lineNode = lineElement;
                    } else {
                        continue;
                    }

                    // 每行是 {"0":"message","_meta":{...},"time":"..."}
                    String message = lineNode.path("0").asText(null);
                    String time = lineNode.path("time").asText(null);
                    String level = lineNode.path("_meta").path("logLevelName").asText("INFO");

                    if (message != null && !message.isEmpty()) {
                        String formattedLine = String.format("[%s] [%s] %s",
                                time != null ? time : "", level, message);
                        sb.append(formattedLine).append("\n");
                    }
                } catch (Exception e) {
                    log.debug("[LogStreamService] Failed to parse line {}: {}", i, e.getMessage());
                }
            }

            return new LogResult(sb.toString(), newCursor);
        } catch (Exception e) {
            log.error("[LogStreamService] Failed to extract log content: {}", e.getMessage());
            return new LogResult(null, null);
        }
    }

    /**
     * 日志流上下文
     */
    private static class LogStreamContext {
        final SseEmitter emitter;
        final Long workerId;
        volatile ScheduledFuture<?> task;
        volatile boolean active = true;
        volatile Integer cursor = null;  // 上次获取的日志位置

        LogStreamContext(SseEmitter emitter, Long workerId) {
            this.emitter = emitter;
            this.workerId = workerId;
        }

        boolean isActive() {
            return active;
        }

        void send(String content) {
            try {
                String data = "{\"type\":\"log\",\"content\":" +
                        new ObjectMapper().writeValueAsString(content) + "}";
                emitter.send(SseEmitter.event().data(data));
            } catch (IOException e) {
                log.debug("Failed to send log to emitter: {}", e.getMessage());
                active = false;
            }
        }

        void stop() {
            active = false;
            if (task != null) {
                task.cancel(true);
            }
            try {
                emitter.complete();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
