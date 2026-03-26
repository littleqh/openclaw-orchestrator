package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.Operation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface OperationRepository extends JpaRepository<Operation, Long> {

    @Query("SELECT DISTINCT o FROM Operation o LEFT JOIN FETCH o.skills WHERE o.id = :id")
    Optional<Operation> findByIdWithSkills(@Param("id") Long id);

    boolean existsByName(String name);
}
