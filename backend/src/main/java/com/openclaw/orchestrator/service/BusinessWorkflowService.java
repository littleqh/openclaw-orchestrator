package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.workflow.*;
import com.openclaw.orchestrator.entity.*;
import com.openclaw.orchestrator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusinessWorkflowService {

    private final BusinessWorkflowRepository workflowRepository;
    private final WorkflowStageRepository stageRepository;
    private final WorkflowStageBranchRepository branchRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final OutputSchemaRepository outputSchemaRepository;

    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public WorkflowResponse getWorkflow(Long id) {
        BusinessWorkflow workflow = workflowRepository.findByIdWithStages(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Workflow not found"));
        return toResponse(workflow);
    }

    @Transactional
    public WorkflowResponse createWorkflow(WorkflowRequest request, Long createdBy) {
        BusinessWorkflow workflow = BusinessWorkflow.builder()
                .name(request.getName())
                .description(request.getDescription())
                .defaultMaxRetries(request.getDefaultMaxRetries() != null ? request.getDefaultMaxRetries() : 3)
                .isActive(true)
                .createdBy(createdBy)
                .build();

        workflow = workflowRepository.save(workflow);

        if (request.getStages() != null) {
            for (StageRequest stageReq : request.getStages()) {
                WorkflowStage stage = createStage(workflow, stageReq);
                workflow.getStages().add(stage);
            }
        }

        return toResponse(workflow);
    }

    @Transactional
    public WorkflowResponse updateWorkflow(Long id, WorkflowRequest request) {
        System.out.println("[DEBUG] updateWorkflow called, id=" + id);
        System.out.println("[DEBUG] Request stages: " + (request.getStages() != null ? request.getStages().size() : 0));
        if (request.getStages() != null) {
            for (var s : request.getStages()) {
                System.out.println("[DEBUG]   Stage in request: id=" + s.getId() + ", name=" + s.getName() + ", order=" + s.getStageOrder());
            }
        }

        BusinessWorkflow workflow = workflowRepository.findByIdWithStages(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Workflow not found"));

        System.out.println("[DEBUG] Existing stages in DB:");
        for (WorkflowStage s : workflow.getStages()) {
            System.out.println("[DEBUG]   Stage in DB: id=" + s.getId() + ", name=" + s.getName() + ", order=" + s.getStageOrder());
        }

        // 系统任务不可修改内容
        if (Boolean.TRUE.equals(workflow.getIsSystem())) {
            throw new RuntimeException("FORBIDDEN:Cannot modify system workflow");
        }

        if (!workflow.getInstances().isEmpty()) {
            throw new RuntimeException("FORBIDDEN:Cannot modify workflow with running instances");
        }

        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        if (request.getDefaultMaxRetries() != null) {
            workflow.setDefaultMaxRetries(request.getDefaultMaxRetries());
        }

        // Update existing stages or create new ones
        if (request.getStages() != null) {
            // Get existing stage IDs from the request
            java.util.Map<Long, WorkflowStage> existingStagesMap = new java.util.HashMap<>();
            for (WorkflowStage s : workflow.getStages()) {
                existingStagesMap.put(s.getId(), s);
            }

            // Clear the collection but keep track of which ones to remove
            java.util.Set<Long> newStageIds = new java.util.HashSet<>();
            for (StageRequest stageReq : request.getStages()) {
                if (stageReq.getId() != null && existingStagesMap.containsKey(stageReq.getId())) {
                    // Update existing stage
                    WorkflowStage existing = existingStagesMap.get(stageReq.getId());
                    existing.setName(stageReq.getName());
                    existing.setDescription(stageReq.getDescription());
                    existing.setStageOrder(stageReq.getStageOrder());
                    existing.setTaskType(stageReq.getTaskType() != null ?
                            WorkflowStage.TaskType.valueOf(stageReq.getTaskType()) : WorkflowStage.TaskType.AUTO);
                    existing.setWorkerId(stageReq.getWorkerId());
                    existing.setApproverId(stageReq.getApproverId());
                    existing.setOutputSchemaId(stageReq.getOutputSchemaId());
                    existing.setMaxRetries(stageReq.getMaxRetries());
                    existing.setPriority(stageReq.getPriority() != null ? stageReq.getPriority() : 0);
                    existing.setNextStageId(stageReq.getNextStageId());
                    existing.setConditionExpr(stageReq.getConditionExpr());
                    existing.setX(stageReq.getX());
                    existing.setY(stageReq.getY());
                    newStageIds.add(stageReq.getId());
                    System.out.println("[DEBUG] Updated existing stage: " + stageReq.getId());
                } else {
                    // Create new stage
                    WorkflowStage stage = createStage(workflow, stageReq);
                    workflow.getStages().add(stage);
                    if (stageReq.getId() != null) {
                        newStageIds.add(stageReq.getId());
                    }
                    System.out.println("[DEBUG] Created new stage");
                }
            }

            // Remove stages that are no longer in the request
            workflow.getStages().removeIf(s -> !newStageIds.contains(s.getId()));
        }

        BusinessWorkflow saved = workflowRepository.save(workflow);
        System.out.println("[DEBUG] After save, stages in DB:");
        for (WorkflowStage s : saved.getStages()) {
            System.out.println("[DEBUG]   Stage after save: id=" + s.getId() + ", name=" + s.getName());
        }

        return toResponse(saved);
    }

    private WorkflowStage createStage(BusinessWorkflow workflow, StageRequest request) {
        WorkflowStage stage = WorkflowStage.builder()
                .workflow(workflow)
                .name(request.getName())
                .description(request.getDescription())
                .stageOrder(request.getStageOrder())
                .taskType(request.getTaskType() != null ?
                        WorkflowStage.TaskType.valueOf(request.getTaskType()) : WorkflowStage.TaskType.AUTO)
                .workerId(request.getWorkerId())
                .approverId(request.getApproverId())
                .outputSchemaId(request.getOutputSchemaId())
                .maxRetries(request.getMaxRetries())
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .nextStageId(request.getNextStageId())
                .conditionExpr(request.getConditionExpr())
                .x(request.getX())
                .y(request.getY())
                .build();

        stage = stageRepository.save(stage);

        if (request.getBranches() != null) {
            for (BranchRequest branchReq : request.getBranches()) {
                WorkflowStageBranch branch = WorkflowStageBranch.builder()
                        .stage(stage)
                        .targetStageId(branchReq.getTargetStageId())
                        .conditionExpr(branchReq.getConditionExpr())
                        .branchOrder(branchReq.getBranchOrder() != null ? branchReq.getBranchOrder() : 0)
                        .build();
                stage.getBranches().add(branchRepository.save(branch));
            }
        }

        return stage;
    }

    @Transactional
    public void deleteWorkflow(Long id) {
        BusinessWorkflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Workflow not found"));
        // 系统任务不可删除
        if (Boolean.TRUE.equals(workflow.getIsSystem())) {
            throw new RuntimeException("FORBIDDEN:Cannot delete system workflow");
        }
        workflow.setIsActive(false);
        workflowRepository.save(workflow);
    }

    public List<OutputSchema> listOutputSchemas() {
        return outputSchemaRepository.findAll();
    }

    private WorkflowResponse toResponse(BusinessWorkflow workflow) {
        return WorkflowResponse.builder()
                .id(workflow.getId())
                .name(workflow.getName())
                .description(workflow.getDescription())
                .defaultMaxRetries(workflow.getDefaultMaxRetries())
                .isActive(workflow.getIsActive())
                .isSystem(workflow.getIsSystem())
                .createdBy(workflow.getCreatedBy())
                .createdAt(workflow.getCreatedAt())
                .updatedAt(workflow.getUpdatedAt())
                .stages(workflow.getStages().stream()
                        .map(this::toStageResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    private StageResponse toStageResponse(WorkflowStage stage) {
        String workerName = stage.getWorkerId() != null
                ? workerRepository.findById(stage.getWorkerId()).map(w -> w.getName()).orElse(null)
                : null;

        String approverName = stage.getApproverId() != null
                ? userRepository.findById(stage.getApproverId()).map(u -> u.getUsername()).orElse(null)
                : null;

        String schemaName = stage.getOutputSchemaId() != null
                ? outputSchemaRepository.findById(stage.getOutputSchemaId()).map(s -> s.getName()).orElse(null)
                : null;

        return StageResponse.builder()
                .id(stage.getId())
                .workflowId(stage.getWorkflow().getId())
                .name(stage.getName())
                .description(stage.getDescription())
                .stageOrder(stage.getStageOrder())
                .taskType(stage.getTaskType().name())
                .workerId(stage.getWorkerId())
                .workerName(workerName)
                .approverId(stage.getApproverId())
                .approverName(approverName)
                .outputSchemaId(stage.getOutputSchemaId())
                .outputSchemaName(schemaName)
                .maxRetries(stage.getMaxRetries())
                .priority(stage.getPriority())
                .nextStageId(stage.getNextStageId())
                .conditionExpr(stage.getConditionExpr())
                .x(stage.getX())
                .y(stage.getY())
                .branches(stage.getBranches().stream()
                        .map(this::toBranchResponse)
                        .collect(Collectors.toList()))
                .createdAt(stage.getCreatedAt())
                .build();
    }

    private BranchResponse toBranchResponse(WorkflowStageBranch branch) {
        return BranchResponse.builder()
                .id(branch.getId())
                .stageId(branch.getStage().getId())
                .targetStageId(branch.getTargetStageId())
                .conditionExpr(branch.getConditionExpr())
                .branchOrder(branch.getBranchOrder())
                .build();
    }
}
