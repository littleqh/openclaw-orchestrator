package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.WorkflowStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowStageRepository extends JpaRepository<WorkflowStage, Long> {

    List<WorkflowStage> findByWorkflowIdOrderByStageOrderAsc(Long workflowId);

    @Query("SELECT s FROM WorkflowStage s LEFT JOIN FETCH s.branches WHERE s.id = :id")
    Optional<WorkflowStage> findByIdWithBranches(Long id);

    Optional<WorkflowStage> findFirstByWorkflowIdOrderByStageOrderAsc(Long workflowId);

    Optional<WorkflowStage> findByWorkflowIdAndStageOrder(Long workflowId, Integer stageOrder);
}
