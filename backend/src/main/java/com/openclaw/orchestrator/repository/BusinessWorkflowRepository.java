package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.BusinessWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessWorkflowRepository extends JpaRepository<BusinessWorkflow, Long> {

    @Query("SELECT DISTINCT w FROM BusinessWorkflow w LEFT JOIN FETCH w.stages s LEFT JOIN FETCH s.branches WHERE w.isActive = true")
    List<BusinessWorkflow> findByIsActiveTrue();

    @Query("SELECT DISTINCT w FROM BusinessWorkflow w LEFT JOIN FETCH w.stages s LEFT JOIN FETCH s.branches WHERE w.id = :id")
    Optional<BusinessWorkflow> findByIdWithStages(Long id);
}
