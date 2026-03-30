package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.LLMModel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LLMModelRepository extends JpaRepository<LLMModel, Long> {
    List<LLMModel> findByEnabledTrue();
    boolean existsByName(String name);
    boolean existsByProviderAndModel(String provider, String model);
}
