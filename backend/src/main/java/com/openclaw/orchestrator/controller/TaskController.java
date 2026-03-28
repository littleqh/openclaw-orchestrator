package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.workflow.ApprovalRequest;
import com.openclaw.orchestrator.dto.workflow.TaskCompleteRequest;
import com.openclaw.orchestrator.dto.workflow.TaskResponse;
import com.openclaw.orchestrator.entity.TaskArtifact;
import com.openclaw.orchestrator.entity.TaskExecutionLog;
import com.openclaw.orchestrator.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class TaskController {

    private final TaskService taskService;

    // ==================== Worker API ====================

    /**
     * Worker polls for pending task
     */
    @GetMapping("/agent/tasks/pending")
    public ResponseEntity<TaskResponse> getPendingTask(Authentication authentication) {
        Long workerId = getWorkerId(authentication);
        if (workerId == null) {
            return ResponseEntity.status(401).build();
        }
        TaskResponse task = taskService.getPendingTaskForWorker(workerId);
        if (task == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(task);
    }

    /**
     * Worker claims a task (starts processing)
     */
    @PostMapping("/agent/tasks/{id}/claim")
    public ResponseEntity<TaskResponse> claimTask(@PathVariable Long id, Authentication authentication) {
        Long workerId = getWorkerId(authentication);
        if (workerId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(taskService.claimTask(id, workerId));
    }

    /**
     * Worker completes a task
     */
    @PostMapping("/agent/tasks/{id}/complete")
    public ResponseEntity<Void> completeTask(
            @PathVariable Long id,
            @RequestBody TaskCompleteRequest request,
            Authentication authentication) {
        Long workerId = getWorkerId(authentication);
        if (workerId == null) {
            return ResponseEntity.status(401).build();
        }
        taskService.completeTask(id, request, workerId);
        return ResponseEntity.ok().build();
    }

    /**
     * Worker uploads artifact
     */
    @PostMapping("/agent/tasks/{id}/artifacts")
    public ResponseEntity<TaskArtifact> uploadArtifact(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "result") String fileType,
            Authentication authentication) {
        Long workerId = getWorkerId(authentication);
        if (workerId == null) {
            return ResponseEntity.status(401).build();
        }
        // TODO: Save file to storage and get path
        String filePath = "/uploads/" + file.getOriginalFilename();
        TaskArtifact artifact = taskService.uploadArtifact(
                id, file.getOriginalFilename(), filePath, fileType, file.getSize());
        return ResponseEntity.ok(artifact);
    }

    // ==================== Admin Approval API ====================

    /**
     * Get pending approvals for current admin
     */
    @GetMapping("/admin/approvals/pending")
    public ResponseEntity<List<TaskResponse>> getPendingApprovals(Authentication authentication) {
        Long adminId = getUserId(authentication);
        if (adminId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(taskService.getPendingApprovals(adminId));
    }

    /**
     * Process approval (approve or reject)
     */
    @PostMapping("/admin/approvals/{taskId}")
    public ResponseEntity<Void> processApproval(
            @PathVariable Long taskId,
            @RequestBody ApprovalRequest request,
            Authentication authentication) {
        Long approverId = getUserId(authentication);
        if (approverId == null) {
            return ResponseEntity.status(401).build();
        }
        taskService.processApproval(taskId, request, approverId);
        return ResponseEntity.ok().build();
    }

    // ==================== Admin Task Control API ====================

    /**
     * Admin forces task completion
     */
    @PostMapping("/admin/tasks/{id}/force-complete")
    public ResponseEntity<Void> forceComplete(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long adminId = getUserId(authentication);
        if (adminId == null) {
            return ResponseEntity.status(401).build();
        }
        String output = body != null ? body.get("output") : null;
        taskService.forceComplete(id, output, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * Admin forces task failure
     */
    @PostMapping("/admin/tasks/{id}/force-fail")
    public ResponseEntity<Void> forceFail(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        Long adminId = getUserId(authentication);
        if (adminId == null) {
            return ResponseEntity.status(401).build();
        }
        String reason = body != null ? body.get("reason") : "Admin forced failure";
        taskService.forceFail(id, reason, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * Admin pauses a task
     */
    @PostMapping("/admin/tasks/{id}/pause")
    public ResponseEntity<Void> pauseTask(@PathVariable Long id, Authentication authentication) {
        Long adminId = getUserId(authentication);
        if (adminId == null) {
            return ResponseEntity.status(401).build();
        }
        taskService.pauseTask(id, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * Admin resumes a task
     */
    @PostMapping("/admin/tasks/{id}/resume")
    public ResponseEntity<Void> resumeTask(@PathVariable Long id, Authentication authentication) {
        Long adminId = getUserId(authentication);
        if (adminId == null) {
            return ResponseEntity.status(401).build();
        }
        taskService.resumeTask(id, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * Admin resets a task
     */
    @PostMapping("/admin/tasks/{id}/reset")
    public ResponseEntity<Void> resetTask(@PathVariable Long id, Authentication authentication) {
        Long adminId = getUserId(authentication);
        if (adminId == null) {
            return ResponseEntity.status(401).build();
        }
        taskService.resetTask(id, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * Get task details
     */
    @GetMapping("/admin/tasks/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTask(id));
    }

    /**
     * Get task execution logs
     */
    @GetMapping("/admin/tasks/{id}/logs")
    public ResponseEntity<List<TaskExecutionLog>> getTaskLogs(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskLogs(id));
    }

    private Long getWorkerId(Authentication authentication) {
        if (authentication == null) return null;
        // Worker authentication uses Agent token, name format is "AGENT:workerId" or just workerId
        String name = authentication.getName();
        if (name.startsWith("AGENT:")) {
            try {
                return Long.parseLong(name.substring(6));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            return null;
        }
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
