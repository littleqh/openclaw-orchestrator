package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.workflow.ApprovalRequest;
import com.openclaw.orchestrator.dto.workflow.TaskCompleteRequest;
import com.openclaw.orchestrator.dto.workflow.TaskResponse;
import com.openclaw.orchestrator.entity.Task;
import com.openclaw.orchestrator.entity.TaskArtifact;
import com.openclaw.orchestrator.entity.TaskExecutionLog;
import com.openclaw.orchestrator.entity.WorkflowStage;
import com.openclaw.orchestrator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskExecutionLogRepository taskLogRepository;
    private final TaskArtifactRepository artifactRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final WorkflowInstanceService instanceService;

    // ==================== Worker API ====================

    /**
     * Worker polls for pending task assigned to them.
     * Returns one task at a time (highest priority, oldest first)
     */
    public TaskResponse getPendingTaskForWorker(Long workerId) {
        Task task = taskRepository.findPendingTaskForWorker(workerId)
                .orElse(null);
        return task != null ? toTaskResponse(task) : null;
    }

    /**
     * Worker claims a task (changes status from PENDING to PROCESSING)
     */
    @Transactional
    public TaskResponse claimTask(Long taskId, Long workerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        if (!workerId.equals(task.getWorkerId())) {
            throw new RuntimeException("FORBIDDEN:Task not assigned to this worker");
        }

        if (task.getStatus() != Task.TaskStatus.PENDING) {
            throw new RuntimeException("INVALID:Task is not in pending status");
        }

        String oldStatus = task.getStatus().name();
        task.setStatus(Task.TaskStatus.PROCESSING);
        task = taskRepository.save(task);

        logTaskAction(task, "CLAIM", oldStatus, "PROCESSING", String.valueOf(workerId), "Task claimed by worker");

        return toTaskResponse(task);
    }

    /**
     * Worker completes a task with output
     */
    @Transactional
    public void completeTask(Long taskId, TaskCompleteRequest request, Long workerId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        if (!workerId.equals(task.getWorkerId())) {
            throw new RuntimeException("FORBIDDEN:Task not assigned to this worker");
        }

        if (task.getStatus() != Task.TaskStatus.PROCESSING) {
            throw new RuntimeException("INVALID:Task is not in processing status");
        }

        // Validate output against schema if defined
        if (task.getStage().getOutputSchemaId() != null && request.getOutput() != null) {
            validateOutput(task, request.getOutput());
        }

        // Complete task and advance workflow
        instanceService.completeTaskAndAdvance(taskId, request.getOutput(), String.valueOf(workerId));
    }

    private void validateOutput(Task task, String output) {
        // TODO: Implement JSON Schema validation
        // For now, just check if it's valid JSON
        if (output != null && !output.trim().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.readTree(output);
            } catch (Exception e) {
                throw new RuntimeException("INVALID:Output is not valid JSON");
            }
        }
    }

    // ==================== Approval API ====================

    /**
     * Admin approves or rejects a task
     */
    @Transactional
    public void processApproval(Long taskId, ApprovalRequest request, Long approverId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        if (task.getStage().getTaskType() != WorkflowStage.TaskType.APPROVAL) {
            throw new RuntimeException("INVALID:Task is not an approval type");
        }

        if (!approverId.equals(task.getApproverId())) {
            throw new RuntimeException("FORBIDDEN:Task not assigned to this approver");
        }

        if (task.getStatus() != Task.TaskStatus.PENDING) {
            throw new RuntimeException("INVALID:Task is not pending approval");
        }

        String action = request.getAction();
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new RuntimeException("INVALID:Action must be approve or reject");
        }

        String oldStatus = task.getStatus().name();
        if ("approve".equals(action)) {
            task.setStatus(Task.TaskStatus.APPROVED);
            task.setApproverComments(request.getComments());
            task = taskRepository.save(task);
            logTaskAction(task, "APPROVE", oldStatus, "APPROVED", String.valueOf(approverId), request.getComments());

            // Advance workflow
            instanceService.completeTaskAndAdvance(taskId, task.getOutput(), String.valueOf(approverId));
        } else {
            task.setStatus(Task.TaskStatus.REJECTED);
            task.setApproverComments(request.getComments());
            task = taskRepository.save(task);
            logTaskAction(task, "REJECT", oldStatus, "REJECTED", String.valueOf(approverId), request.getComments());

            // Fail workflow
            instanceService.failTask(taskId, "Rejected by approver: " + request.getComments());
        }
    }

    // ==================== Admin Task Control ====================

    /**
     * Admin forces task completion
     */
    @Transactional
    public void forceComplete(Long taskId, String output, Long adminId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        String oldStatus = task.getStatus().name();
        task.setOutput(output);
        task.setStatus(Task.TaskStatus.COMPLETED);
        task = taskRepository.save(task);

        logTaskAction(task, "ADMIN_FORCE_COMPLETE", oldStatus, "COMPLETED", String.valueOf(adminId), "Admin forced completion");

        instanceService.completeTaskAndAdvance(taskId, output, String.valueOf(adminId));
    }

    /**
     * Admin forces task failure
     */
    @Transactional
    public void forceFail(Long taskId, String reason, Long adminId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        String oldStatus = task.getStatus().name();
        task.setStatus(Task.TaskStatus.FAILED);
        task = taskRepository.save(task);

        logTaskAction(task, "ADMIN_FORCE_FAIL", oldStatus, "FAILED", String.valueOf(adminId), reason);

        instanceService.failTask(taskId, "Admin forced failure: " + reason);
    }

    /**
     * Admin pauses a task
     */
    @Transactional
    public void pauseTask(Long taskId, Long adminId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        if (task.getStatus() != Task.TaskStatus.PROCESSING && task.getStatus() != Task.TaskStatus.PENDING) {
            throw new RuntimeException("INVALID:Task cannot be paused");
        }

        String oldStatus = task.getStatus().name();
        task.setStatus(Task.TaskStatus.PAUSED);
        task = taskRepository.save(task);

        logTaskAction(task, "ADMIN_PAUSE", oldStatus, "PAUSED", String.valueOf(adminId), "Admin paused task");
    }

    /**
     * Admin resumes a task
     */
    @Transactional
    public void resumeTask(Long taskId, Long adminId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        if (task.getStatus() != Task.TaskStatus.PAUSED) {
            throw new RuntimeException("INVALID:Task is not paused");
        }

        String oldStatus = task.getStatus().name();
        task.setStatus(Task.TaskStatus.PENDING);
        task = taskRepository.save(task);

        logTaskAction(task, "ADMIN_RESUME", oldStatus, "PENDING", String.valueOf(adminId), "Admin resumed task");
    }

    /**
     * Admin resets a task (clear retry count, set to pending)
     */
    @Transactional
    public void resetTask(Long taskId, Long adminId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        String oldStatus = task.getStatus().name();
        task.setRetryCount(0);
        task.setStatus(Task.TaskStatus.PENDING);
        task = taskRepository.save(task);

        logTaskAction(task, "ADMIN_RESET", oldStatus, "PENDING", String.valueOf(adminId), "Admin reset task");
    }

    // ==================== File Upload ====================

    @Transactional
    public TaskArtifact uploadArtifact(Long taskId, String fileName, String filePath, String fileType, Long fileSize) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        TaskArtifact artifact = TaskArtifact.builder()
                .taskId(taskId)
                .fileName(fileName)
                .filePath(filePath)
                .fileType(fileType)
                .fileSize(fileSize)
                .build();

        return artifactRepository.save(artifact);
    }

    // ==================== Query Methods ====================

    public TaskResponse getTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));
        return toTaskResponse(task);
    }

    public List<TaskResponse> getTasksByInstance(Long instanceId) {
        return taskRepository.findByInstanceId(instanceId).stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getPendingApprovals(Long approverId) {
        return taskRepository.findPendingApprovals(approverId).stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());
    }

    public List<TaskExecutionLog> getTaskLogs(Long taskId) {
        return taskLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    public List<TaskArtifact> getTaskArtifacts(Long taskId) {
        return artifactRepository.findByTaskId(taskId);
    }

    private void logTaskAction(Task task, String action, String oldStatus, String newStatus, String operator, String comment) {
        TaskExecutionLog log = TaskExecutionLog.builder()
                .taskId(task.getId())
                .action(action)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .operator(operator)
                .comment(comment)
                .build();
        taskLogRepository.save(log);
    }

    private TaskResponse toTaskResponse(Task task) {
        String workerName = task.getWorkerId() != null ?
                workerRepository.findById(task.getWorkerId()).map(w -> w.getName()).orElse(null) : null;

        String approverName = task.getApproverId() != null ?
                userRepository.findById(task.getApproverId()).map(u -> u.getUsername()).orElse(null) : null;

        return TaskResponse.builder()
                .id(task.getId())
                .instanceId(task.getInstance().getId())
                .stageId(task.getStage().getId())
                .stageName(task.getStage().getName())
                .workerId(task.getWorkerId())
                .workerName(workerName)
                .type(task.getStage().getTaskType().name())
                .status(task.getStatus().name())
                .priority(task.getPriority())
                .retryCount(task.getRetryCount())
                .maxRetries(task.getMaxRetries())
                .output(task.getOutput())
                .approverId(task.getApproverId())
                .approverName(approverName)
                .approverComments(task.getApproverComments())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
