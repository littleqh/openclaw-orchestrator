package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.StatusData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseEmitterService {

    // instanceId -> List of SSE emitters for that instance
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // SSE timeout: 30 seconds (Spring default)
    private static final long SSE_TIMEOUT = 30_000L;

    /**
     * Register a new SSE connection for an instance.
     * Returns the SseEmitter for the caller to use.
     */
    public SseEmitter subscribe(Long instanceId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitters.computeIfAbsent(instanceId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for instance {}", instanceId);
            removeEmitter(instanceId, emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE connection timeout for instance {}", instanceId);
            removeEmitter(instanceId, emitter);
        });
        emitter.onError(e -> {
            log.debug("SSE connection error for instance {}: {}", instanceId, e.getMessage());
            removeEmitter(instanceId, emitter);
        });

        log.info("New SSE subscription for instance {}, total connections: {}",
                instanceId, emitters.get(instanceId).size());

        return emitter;
    }

    /**
     * Send StatusData to all subscribers of an instance.
     */
    public void broadcast(Long instanceId, StatusData data) {
        List<SseEmitter> list = emitters.get(instanceId);
        if (list == null || list.isEmpty()) {
            return;
        }

        List<SseEmitter> dead = new java.util.ArrayList<>();

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(data));
            } catch (IOException e) {
                log.debug("Failed to send to emitter, will remove: {}", e.getMessage());
                dead.add(emitter);
            }
        }

        // Clean up dead emitters
        for (SseEmitter deadEmitter : dead) {
            removeEmitter(instanceId, deadEmitter);
        }
    }

    /**
     * Send keepalive comment to keep connection alive.
     */
    public void sendHeartbeat(Long instanceId) {
        List<SseEmitter> list = emitters.get(instanceId);
        if (list == null) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException e) {
                // ignore, will be cleaned up on next broadcast
            }
        }
    }

    /**
     * Get number of active connections for an instance.
     */
    public int getConnectionCount(Long instanceId) {
        List<SseEmitter> list = emitters.get(instanceId);
        return list == null ? 0 : list.size();
    }

    private void removeEmitter(Long instanceId, SseEmitter emitter) {
        emitters.compute(instanceId, (id, list) -> {
            if (list == null) return null;
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
