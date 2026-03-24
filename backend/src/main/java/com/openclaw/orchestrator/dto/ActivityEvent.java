package com.openclaw.orchestrator.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityEvent {
    private LocalDateTime time;
    private String type;   // session_created, session_ended, session_error, agent_registered, agent_unregistered
    private String detail;

    public static final String SESSION_CREATED = "session_created";
    public static final String SESSION_ENDED = "session_ended";
    public static final String SESSION_ERROR = "session_error";
    public static final String AGENT_REGISTERED = "agent_registered";
    public static final String AGENT_UNREGISTERED = "agent_unregistered";
}
