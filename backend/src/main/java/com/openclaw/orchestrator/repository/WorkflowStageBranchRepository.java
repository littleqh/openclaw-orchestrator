package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.WorkflowStageBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowStageBranchRepository extends JpaRepository<WorkflowStageBranch, Long> {

    List<WorkflowStageBranch> findByStageIdOrderByBranchOrderAsc(Long stageId);
}
