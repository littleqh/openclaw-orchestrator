package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByWorkerIdOrderByUpdatedAtDesc(Long workerId);
    List<ChatSession> findByWorkerIdAndArchivedOrderByUpdatedAtDesc(Long workerId, Boolean archived);
}
