package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.WorkflowInstanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowInstanceLogRepository extends JpaRepository<WorkflowInstanceLog, Long> {

    List<WorkflowInstanceLog> findByInstanceIdOrderByCreatedAtAsc(Long instanceId);
}
