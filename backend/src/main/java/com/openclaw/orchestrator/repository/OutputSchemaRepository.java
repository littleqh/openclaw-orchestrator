package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.OutputSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutputSchemaRepository extends JpaRepository<OutputSchema, Long> {
}
