package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    List<Worker> findAllByOrderByCreatedAtDesc();

    @Query("SELECT w FROM Worker w LEFT JOIN FETCH w.skills WHERE w.id = :id")
    Optional<Worker> findByIdWithSkills(Long id);

    List<Worker> findByNameContainingIgnoreCaseOrNicknameContainingIgnoreCase(String name, String nickname);
}