package com.openclaw.orchestrator.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OperationDetail {
    private Long id;
    private String name;
    private String description;
    private List<SkillDetail> skills;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
