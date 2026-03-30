package com.openclaw.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LLMChunk {
    private String content;   // actual response content
    private String thinking;  // reasoning_content (thinking process)
}
