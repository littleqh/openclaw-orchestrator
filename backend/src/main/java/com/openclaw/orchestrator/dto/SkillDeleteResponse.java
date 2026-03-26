package com.openclaw.orchestrator.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SkillDeleteResponse {
    private boolean referenced;
    private List<OperationRef> referencedBy;

    @Data
    @Builder
    public static class OperationRef {
        private Long id;
        private String name;
    }
}
