package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.AgentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AgentTokenRepository extends JpaRepository<AgentToken, Long> {
    Optional<AgentToken> findByToken(String token);

    Optional<AgentToken> findByWorkerId(Long workerId);

    @Query("SELECT at FROM AgentToken at LEFT JOIN FETCH at.worker WHERE at.worker IS NULL")
    Optional<AgentToken> findSystemToken();

    @Query("SELECT at FROM AgentToken at LEFT JOIN FETCH at.worker WHERE at.worker IS NOT NULL")
    List<AgentToken> findAllAgentTokens();

    boolean existsByWorkerId(Long workerId);
}