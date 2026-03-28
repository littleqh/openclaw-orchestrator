package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.TaskArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskArtifactRepository extends JpaRepository<TaskArtifact, Long> {

    List<TaskArtifact> findByTaskId(Long taskId);
}
