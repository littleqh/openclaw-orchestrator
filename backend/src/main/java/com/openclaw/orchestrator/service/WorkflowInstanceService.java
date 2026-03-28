package com.openclaw.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.orchestrator.dto.workflow.InstanceRequest;
import com.openclaw.orchestrator.dto.workflow.InstanceResponse;
import com.openclaw.orchestrator.dto.workflow.TaskResponse;
import com.openclaw.orchestrator.entity.*;
import com.openclaw.orchestrator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowInstanceService {

    private final WorkflowInstanceRepository instanceRepository;
    private final BusinessWorkflowRepository workflowRepository;
    private final WorkflowStageRepository stageRepository;
    private final TaskRepository taskRepository;
    private final TaskExecutionLogRepository taskLogRepository;
    private final WorkflowInstanceLogRepository instanceLogRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public List<InstanceResponse> listInstances() {
        return instanceRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public InstanceResponse getInstance(Long id) {
        WorkflowInstance instance = instanceRepository.findByIdWithTasks(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Instance not found"));
        return toResponse(instance);
    }

    public List<TaskResponse> getInstanceTasks(Long instanceId) {
        return taskRepository.findByInstanceId(instanceId).stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public InstanceResponse startInstance(InstanceRequest request, Long startedBy) {
        BusinessWorkflow workflow = workflowRepository.findByIdWithStages(request.getWorkflowId())
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Workflow not found"));

        // Serialize variables
        String variablesJson = null;
        if (request.getVariables() != null) {
            try {
                variablesJson = objectMapper.writeValueAsString(request.getVariables());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("INVALID:Failed to serialize variables");
            }
        }

        // Create instance in CREATE state
        WorkflowInstance instance = WorkflowInstance.builder()
                .workflow(workflow)
                .status(WorkflowInstance.InstanceStatus.CREATE)
                .description(request.getDescription())
                .variables(variablesJson)
                .startedBy(startedBy)
                .build();

        instance = instanceRepository.save(instance);

        // Log instance creation
        logInstanceAction(instance, "CREATE", null, "CREATE", String.valueOf(startedBy), "Instance created in planning mode");

        // Create all tasks for all stages (in PENDING state, no worker assigned yet)
        createAllTasksForInstance(instance, workflow);

        return toResponse(instance);
    }

    /**
     * 创建实例时同时创建所有环节的任务
     */
    private void createAllTasksForInstance(WorkflowInstance instance, BusinessWorkflow workflow) {
        List<WorkflowStage> stages = stageRepository.findByWorkflowIdOrderByStageOrderAsc(workflow.getId());
        for (WorkflowStage stage : stages) {
            Integer maxRetries = stage.getMaxRetries() != null ? stage.getMaxRetries() :
                    (workflow.getDefaultMaxRetries() != null ? workflow.getDefaultMaxRetries() : 3);

            Task task = Task.builder()
                    .instance(instance)
                    .stage(stage)
                    .workerId(stage.getWorkerId()) // Use stage's default worker
                    .status(Task.TaskStatus.PENDING)
                    .priority(stage.getPriority())
                    .retryCount(0)
                    .maxRetries(maxRetries)
                    .build();

            task = taskRepository.save(task);
            logTaskAction(task, "CREATE", null, "PENDING", "SYSTEM", "Task created for stage: " + stage.getName());
        }
    }

    /**
     * 完成指派 - 将 CREATE 状态转为 PLANNED
     * 所有任务标记为 ASSIGNED（已指派处理人）
     */
    @Transactional
    public InstanceResponse completeAssign(Long id, Long operatorId) {
        WorkflowInstance instance = instanceRepository.findByIdWithTasks(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Instance not found"));

        if (instance.getStatus() != WorkflowInstance.InstanceStatus.CREATE &&
                instance.getStatus() != WorkflowInstance.InstanceStatus.PLANNED) {
            throw new RuntimeException("INVALID:Instance is not in planning state");
        }

        // Mark all tasks as ASSIGNED
        List<Task> tasks = taskRepository.findByInstanceId(id);
        for (Task task : tasks) {
            String oldStatus = task.getStatus().name();
            task.setStatus(Task.TaskStatus.ASSIGNED);
            taskRepository.save(task);
            logTaskAction(task, "ASSIGN", oldStatus, "ASSIGNED", String.valueOf(operatorId), "Task assigned");
        }

        // Update instance status to PLANNED
        String oldStatus = instance.getStatus().name();
        instance.setStatus(WorkflowInstance.InstanceStatus.PLANNED);
        instance = instanceRepository.save(instance);
        logInstanceAction(instance, "COMPLETE_ASSIGN", oldStatus, "PLANNED", String.valueOf(operatorId), "Assignment completed, ready to start");

        return toResponse(instance);
    }

    /**
     * 启动任务 - 将 PLANNED/READY 状态转为 RUNNING
     */
    @Transactional
    public InstanceResponse startInstance(Long id, Long operatorId) {
        WorkflowInstance instance = instanceRepository.findByIdWithTasks(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Instance not found"));

        if (instance.getStatus() != WorkflowInstance.InstanceStatus.PLANNED &&
                instance.getStatus() != WorkflowInstance.InstanceStatus.READY) {
            throw new RuntimeException("INVALID:Instance must be in PLANNED or READY state to start");
        }

        // Find first stage
        WorkflowStage firstStage = stageRepository
                .findFirstByWorkflowIdOrderByStageOrderAsc(instance.getWorkflow().getId())
                .orElseThrow(() -> new RuntimeException("INVALID:Workflow has no stages"));

        instance.setCurrentStage(firstStage);
        String oldStatus = instance.getStatus().name();
        instance.setStatus(WorkflowInstance.InstanceStatus.RUNNING);
        instance = instanceRepository.save(instance);
        logInstanceAction(instance, "START", oldStatus, "RUNNING", String.valueOf(operatorId), "Instance started");

        // Update first task to PENDING and start processing
        Task firstTask = taskRepository.findByInstanceId(id).stream()
                .filter(t -> t.getStage().getId().equals(firstStage.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("INVALID:First task not found"));

        String taskOldStatus = firstTask.getStatus().name();
        firstTask.setStatus(Task.TaskStatus.PENDING);
        taskRepository.save(firstTask);
        logTaskAction(firstTask, "START", taskOldStatus, "PENDING", String.valueOf(operatorId), "First task started");

        return toResponse(instance);
    }

    /**
     * 修改任务的处理人
     */
    @Transactional
    public TaskResponse updateTaskWorker(Long instanceId, Long taskId, Long workerId, Long operatorId) {
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Instance not found"));

        if (instance.getStatus() == WorkflowInstance.InstanceStatus.RUNNING ||
                instance.getStatus() == WorkflowInstance.InstanceStatus.COMPLETED ||
                instance.getStatus() == WorkflowInstance.InstanceStatus.FAILED ||
                instance.getStatus() == WorkflowInstance.InstanceStatus.TERMINATED) {
            throw new RuntimeException("INVALID:Cannot modify worker when instance is running or completed");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        if (!task.getInstance().getId().equals(instanceId)) {
            throw new RuntimeException("INVALID:Task does not belong to this instance");
        }

        Long oldWorkerId = task.getWorkerId();
        task.setWorkerId(workerId);
        taskRepository.save(task);

        logTaskAction(task, "UPDATE_WORKER", String.valueOf(oldWorkerId), String.valueOf(workerId),
                String.valueOf(operatorId), "Worker updated");

        return toTaskResponse(task);
    }

    private void createTaskForStage(WorkflowInstance instance, WorkflowStage stage) {
        // Check if stage already has a pending/processing task
        if (taskRepository.findByInstanceId(instance.getId()).stream()
                .anyMatch(t -> t.getStage().getId().equals(stage.getId()) &&
                        (t.getStatus() == Task.TaskStatus.PENDING || t.getStatus() == Task.TaskStatus.PROCESSING))) {
            return; // Task already exists for this stage
        }

        Integer maxRetries = stage.getMaxRetries() != null ? stage.getMaxRetries() :
                (instance.getWorkflow().getDefaultMaxRetries() != null ? instance.getWorkflow().getDefaultMaxRetries() : 3);

        Task task = Task.builder()
                .instance(instance)
                .stage(stage)
                .workerId(stage.getWorkerId())
                .status(Task.TaskStatus.PENDING)
                .priority(stage.getPriority())
                .retryCount(0)
                .maxRetries(maxRetries)
                .build();

        task = taskRepository.save(task);

        // Log task creation
        logTaskAction(task, "CREATE", null, "PENDING", "SYSTEM", "Task created for stage: " + stage.getName());

        // Send notification for approval type tasks
        if (stage.getTaskType() == WorkflowStage.TaskType.APPROVAL && stage.getApproverId() != null) {
            notificationService.sendApprovalNotification(task, stage.getApproverId());
        }
    }

    @Transactional
    public void pauseInstance(Long id, Long operatorId) {
        WorkflowInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Instance not found"));

        if (instance.getStatus() != WorkflowInstance.InstanceStatus.RUNNING) {
            throw new RuntimeException("INVALID:Instance is not running");
        }

        String oldStatus = instance.getStatus().name();
        instance.setStatus(WorkflowInstance.InstanceStatus.PAUSED);
        instanceRepository.save(instance);

        logInstanceAction(instance, "PAUSE", oldStatus, "PAUSED", String.valueOf(operatorId), "Instance paused");

        // Pause current task if exists
        taskRepository.findPendingTaskByInstance(id).ifPresent(task -> {
            task.setStatus(Task.TaskStatus.PAUSED);
            taskRepository.save(task);
            logTaskAction(task, "PAUSE", "PROCESSING", "PAUSED", String.valueOf(operatorId), "Instance paused");
        });
    }

    @Transactional
    public void resumeInstance(Long id, Long operatorId) {
        WorkflowInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Instance not found"));

        if (instance.getStatus() != WorkflowInstance.InstanceStatus.PAUSED) {
            throw new RuntimeException("INVALID:Instance is not paused");
        }

        String oldStatus = instance.getStatus().name();
        instance.setStatus(WorkflowInstance.InstanceStatus.RUNNING);
        instanceRepository.save(instance);

        logInstanceAction(instance, "RESUME", oldStatus, "RUNNING", String.valueOf(operatorId), "Instance resumed");

        // Resume task if exists
        taskRepository.findPendingTaskByInstance(id).ifPresent(task -> {
            task.setStatus(Task.TaskStatus.PENDING);
            taskRepository.save(task);
            logTaskAction(task, "RESUME", "PAUSED", "PENDING", String.valueOf(operatorId), "Instance resumed");
        });
    }

    @Transactional
    public void terminateInstance(Long id, Long operatorId, String reason) {
        WorkflowInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Instance not found"));

        if (instance.getStatus() == WorkflowInstance.InstanceStatus.COMPLETED ||
                instance.getStatus() == WorkflowInstance.InstanceStatus.TERMINATED) {
            throw new RuntimeException("INVALID:Instance is already terminated or completed");
        }

        String oldStatus = instance.getStatus().name();
        instance.setStatus(WorkflowInstance.InstanceStatus.TERMINATED);
        instance.setCompletedAt(LocalDateTime.now());
        instanceRepository.save(instance);

        logInstanceAction(instance, "TERMINATE", oldStatus, "TERMINATED", String.valueOf(operatorId), reason);
    }

    public List<InstanceResponse> getInstancesByStatus(WorkflowInstance.InstanceStatus status) {
        return instanceRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<WorkflowInstanceLog> getInstanceLogs(Long instanceId) {
        return instanceLogRepository.findByInstanceIdOrderByCreatedAtAsc(instanceId);
    }

    void completeTaskAndAdvance(Long taskId, String output, String operator) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        WorkflowInstance instance = task.getInstance();
        WorkflowStage currentStage = task.getStage();

        // Update task
        String oldStatus = task.getStatus().name();
        task.setOutput(output);
        task.setStatus(Task.TaskStatus.COMPLETED);
        taskRepository.save(task);
        logTaskAction(task, "COMPLETE", oldStatus, "COMPLETED", operator, "Task completed");

        // Evaluate next stage based on conditions
        WorkflowStage nextStage = evaluateNextStage(currentStage, output);

        if (nextStage == null) {
            // No more stages - workflow complete
            completeWorkflow(instance, operator);
        } else {
            // Move to next stage
            instance.setCurrentStage(nextStage);
            instanceRepository.save(instance);
            createTaskForStage(instance, nextStage);
        }
    }

    void failTask(Long taskId, String reason) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Task not found"));

        String oldStatus = task.getStatus().name();
        task.setStatus(Task.TaskStatus.FAILED);
        taskRepository.save(task);
        logTaskAction(task, "FAIL", oldStatus, "FAILED", "SYSTEM", reason);

        // Check retry count
        if (task.getRetryCount() < task.getMaxRetries()) {
            // Will be retried
            task.setStatus(Task.TaskStatus.PENDING);
            task.setRetryCount(task.getRetryCount() + 1);
            taskRepository.save(task);
            logTaskAction(task, "RETRY", "FAILED", "PENDING", "SYSTEM", "Retry " + task.getRetryCount());
        } else {
            // Max retries exceeded - fail workflow
            WorkflowInstance instance = task.getInstance();
            failWorkflow(instance, "Max retries exceeded for task: " + task.getStage().getName());
        }
    }

    private void completeWorkflow(WorkflowInstance instance, String operator) {
        String oldStatus = instance.getStatus().name();
        instance.setStatus(WorkflowInstance.InstanceStatus.COMPLETED);
        instance.setCompletedAt(LocalDateTime.now());
        instanceRepository.save(instance);
        logInstanceAction(instance, "COMPLETE", oldStatus, "COMPLETED", operator, "Workflow completed");
    }

    private void failWorkflow(WorkflowInstance instance, String reason) {
        String oldStatus = instance.getStatus().name();
        instance.setStatus(WorkflowInstance.InstanceStatus.FAILED);
        instance.setCompletedAt(LocalDateTime.now());
        instanceRepository.save(instance);
        logInstanceAction(instance, "FAIL", oldStatus, "FAILED", "SYSTEM", reason);
    }

    private WorkflowStage evaluateNextStage(WorkflowStage currentStage, String output) {
        // Try conditional branches first
        if (!currentStage.getBranches().isEmpty() && output != null) {
            try {
                Map<String, Object> outputMap = objectMapper.readValue(output, new TypeReference<Map<String, Object>>() {});
                for (WorkflowStageBranch branch : currentStage.getBranches()) {
                    if (evaluateCondition(branch.getConditionExpr(), outputMap)) {
                        return stageRepository.findById(branch.getTargetStageId()).orElse(null);
                    }
                }
            } catch (JsonProcessingException e) {
                // Fall through to default next stage
            }
        }

        // Fall back to default next stage
        if (currentStage.getNextStageId() != null) {
            return stageRepository.findById(currentStage.getNextStageId()).orElse(null);
        }

        return null;
    }

    private boolean evaluateCondition(String conditionExpr, Map<String, Object> output) {
        // Simple condition evaluation: output.field == value
        // e.g., "status == true" or "department == IT"
        if (conditionExpr == null || conditionExpr.isEmpty()) {
            return false;
        }

        try {
            // Very simple parser for common cases
            String[] parts = conditionExpr.split("\\s+");
            if (parts.length >= 3) {
                String fieldPath = parts[0];
                String operator = parts[1];
                String expectedValue = parts[2];

                // Get value from output
                Object actualValue = getNestedValue(output, fieldPath);
                if (actualValue == null) return false;

                // Compare
                String actualStr = actualValue.toString();
                if (operator.equals("==")) {
                    return actualStr.equals(expectedValue) ||
                            actualStr.equals(expectedValue.replace("\"", "").replace("'", ""));
                }
            }
        } catch (Exception e) {
            // Condition evaluation failed
        }
        return false;
    }

    private Object getNestedValue(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private void logInstanceAction(WorkflowInstance instance, String action, String oldStatus, String newStatus, String operator, String comment) {
        WorkflowInstanceLog log = WorkflowInstanceLog.builder()
                .instanceId(instance.getId())
                .action(action)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .operator(operator)
                .comment(comment)
                .build();
        instanceLogRepository.save(log);
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

    private InstanceResponse toResponse(WorkflowInstance instance) {
        String workflowName = instance.getWorkflow().getName();

        String currentStageName = null;
        Long currentStageId = null;
        if (instance.getCurrentStage() != null) {
            currentStageId = instance.getCurrentStage().getId();
            currentStageName = instance.getCurrentStage().getName();
        }

        String startedByName = null;
        if (instance.getStartedBy() != null) {
            startedByName = userRepository.findById(instance.getStartedBy())
                    .map(u -> u.getUsername())
                    .orElse(null);
        }

        Map<String, Object> variables = null;
        if (instance.getVariables() != null) {
            try {
                variables = objectMapper.readValue(instance.getVariables(), new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                // Ignore
            }
        }

        // Load tasks if instance has tasks loaded
        List<TaskResponse> taskResponses = null;
        if (instance.getTasks() != null && !instance.getTasks().isEmpty()) {
            taskResponses = instance.getTasks().stream()
                    .map(this::toTaskResponse)
                    .collect(Collectors.toList());
        }

        return InstanceResponse.builder()
                .id(instance.getId())
                .workflowId(instance.getWorkflow().getId())
                .workflowName(workflowName)
                .status(instance.getStatus().name())
                .description(instance.getDescription())
                .currentStageId(currentStageId)
                .currentStageName(currentStageName)
                .variables(variables)
                .startedBy(instance.getStartedBy())
                .startedByName(startedByName)
                .startedAt(instance.getStartedAt())
                .completedAt(instance.getCompletedAt())
                .updatedAt(instance.getUpdatedAt())
                .tasks(taskResponses)
                .build();
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
                .stageDescription(task.getStage().getDescription())
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
