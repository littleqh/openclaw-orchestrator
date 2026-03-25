package com.openclaw.orchestrator.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class NodeDetail {
    private Long id;
    private String tempId;
    private Long workerId;
    private Double x;
    private Double y;
    private String workerName;
    private String workerNickname;
    private String workerAvatar;
}
