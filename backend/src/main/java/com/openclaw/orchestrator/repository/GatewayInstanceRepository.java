package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.GatewayInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GatewayInstanceRepository extends JpaRepository<GatewayInstance, Long> {
    List<GatewayInstance> findAllByOrderByCreatedAtDesc();
}
