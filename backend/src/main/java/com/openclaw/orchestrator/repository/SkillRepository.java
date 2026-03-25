package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    Optional<Skill> findByName(String name);
    boolean existsByName(String name);
}