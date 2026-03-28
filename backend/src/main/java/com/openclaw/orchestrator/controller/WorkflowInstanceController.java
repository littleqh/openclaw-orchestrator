package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.workflow.InstanceRequest;
import com.openclaw.orchestrator.dto.workflow.InstanceResponse;
import com.openclaw.orchestrator.dto.workflow.TaskResponse;
import com.openclaw.orchestrator.entity.Task;
import com.openclaw.orchestrator.entity.WorkflowInstance;
import com.openclaw.orchestrator.entity.WorkflowInstanceLog;
import com.openclaw.orchestrator.repository.WorkflowInstanceRepository;
import com.openclaw.orchestrator.service.TaskService;
import com.openclaw.orchestrator.service.WorkflowInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/instances")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class WorkflowInstanceController {

    private final WorkflowInstanceService instanceService;
    private final WorkflowInstanceRepository instanceRepository;
    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<InstanceResponse>> listInstances(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return ResponseEntity.ok(instanceService.getInstancesByStatus(
                    WorkflowInstance.InstanceStatus.valueOf(status)));
        }
        return ResponseEntity.ok(instanceService.listInstances());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstanceResponse> getInstance(@PathVariable Long id) {
        return ResponseEntity.ok(instanceService.getInstance(id));
    }

    @PostMapping
    public ResponseEntity<InstanceResponse> createInstance(
            @RequestBody InstanceRequest request,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(instanceService.startInstance(request, userId));
    }

    /**
     * 完成指派 - CREATE/PLANNED → PLANNED
     */
    @PostMapping("/{id}/assign")
    public ResponseEntity<InstanceResponse> completeAssign(@PathVariable Long id, Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(instanceService.completeAssign(id, userId));
    }

    /**
     * 启动任务 - PLANNED/READY → RUNNING
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<InstanceResponse> startInstance(@PathVariable Long id, Authentication authentication) {
        Long userId = getUserId(authentication);
        return ResponseEntity.ok(instanceService.startInstance(id, userId));
    }

    /**
     * 修改任务的处理人
     */
    @PutMapping("/{instanceId}/tasks/{taskId}/worker")
    public ResponseEntity<TaskResponse> updateTaskWorker(
            @PathVariable Long instanceId,
            @PathVariable Long taskId,
            @RequestBody Map<String, Long> body,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        Long workerId = body.get("workerId");
        return ResponseEntity.ok(instanceService.updateTaskWorker(instanceId, taskId, workerId, userId));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Void> pauseInstance(@PathVariable Long id, Authentication authentication) {
        Long userId = getUserId(authentication);
        instanceService.pauseInstance(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Void> resumeInstance(@PathVariable Long id, Authentication authentication) {
        Long userId = getUserId(authentication);
        instanceService.resumeInstance(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/terminate")
    public ResponseEntity<Void> terminateInstance(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        String reason = body != null ? body.get("reason") : "Terminated by admin";
        instanceService.terminateInstance(id, userId, reason);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/tasks")
    public ResponseEntity<List<TaskResponse>> getInstanceTasks(@PathVariable Long id) {
        return ResponseEntity.ok(instanceService.getInstanceTasks(id));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<List<WorkflowInstanceLog>> getInstanceLogs(@PathVariable Long id) {
        return ResponseEntity.ok(instanceService.getInstanceLogs(id));
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null) return null;
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
