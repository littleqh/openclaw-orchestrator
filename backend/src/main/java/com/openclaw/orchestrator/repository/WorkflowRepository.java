package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {

    @Query("SELECT DISTINCT w FROM Workflow w LEFT JOIN FETCH w.nodes n LEFT JOIN FETCH n.worker LEFT JOIN FETCH w.edges e LEFT JOIN FETCH e.sourceNode LEFT JOIN FETCH e.targetNode WHERE w.id = :id")
    Optional<Workflow> findByIdWithDetails(@Param("id") Long id);
}
