package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {

    List<WorkflowInstance> findByWorkflowId(Long workflowId);

    List<WorkflowInstance> findByStatus(WorkflowInstance.InstanceStatus status);

    @Query("SELECT DISTINCT i FROM WorkflowInstance i LEFT JOIN FETCH i.tasks t LEFT JOIN FETCH t.stage WHERE i.id = :id")
    Optional<WorkflowInstance> findByIdWithTasks(Long id);

    List<WorkflowInstance> findByStartedBy(Long startedBy);
}
