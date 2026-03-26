package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.Operation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OperationRepository extends JpaRepository<Operation, Long> {

    @Query("SELECT DISTINCT o FROM Operation o LEFT JOIN FETCH o.skills LEFT JOIN FETCH o.workers WHERE o.id = :id")
    Optional<Operation> findByIdWithSkills(@Param("id") Long id);

    @Query("SELECT DISTINCT o FROM Operation o LEFT JOIN FETCH o.skills LEFT JOIN FETCH o.workers")
    List<Operation> findAllWithSkills();

    @Query("SELECT DISTINCT o FROM Operation o JOIN o.skills s WHERE s.id = :skillId")
    List<Operation> findBySkillId(@Param("skillId") Long skillId);

    boolean existsByName(String name);
}
