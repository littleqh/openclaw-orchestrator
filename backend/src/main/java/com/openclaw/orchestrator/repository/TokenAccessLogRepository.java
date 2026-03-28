package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.TokenAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TokenAccessLogRepository extends JpaRepository<TokenAccessLog, Long> {
    List<TokenAccessLog> findByAgentTokenIdOrderByAccessTimeDesc(Long tokenId);
}