package com.openclaw.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayConnectResult {
    private String status;  // connected, pairing_required, error
    private String message;
    private String errorCode;
    private List<String> logs;
}
