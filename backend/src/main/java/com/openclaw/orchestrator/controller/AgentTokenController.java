package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.TokenCreateRequest;
import com.openclaw.orchestrator.dto.TokenResponse;
import com.openclaw.orchestrator.service.AgentTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AgentTokenController {

    private final AgentTokenService agentTokenService;

    @GetMapping
    public ResponseEntity<List<TokenResponse>> list() {
        return ResponseEntity.ok(agentTokenService.getAllTokens());
    }

    @PostMapping
    public ResponseEntity<TokenResponse> create(@RequestBody(required = false) TokenCreateRequest request) {
        String token;
        if (request == null || request.getWorkerId() == null) {
            token = agentTokenService.createSystemToken();
        } else {
            token = agentTokenService.createAgentToken(request.getWorkerId());
        }
        // Return full list to get the created token's info
        List<TokenResponse> all = agentTokenService.getAllTokens();
        TokenResponse created = all.stream()
                .filter(t -> t.getToken() != null && t.getToken().equals(token))
                .findFirst()
                .orElseThrow();
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TokenResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(agentTokenService.getTokenInfo(id));
    }

    @PutMapping("/{id}/reset")
    public ResponseEntity<TokenResponse> reset(@PathVariable Long id) {
        String newToken = agentTokenService.resetToken(id);
        return ResponseEntity.ok(agentTokenService.getTokenInfo(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        agentTokenService.deleteToken(id);
        return ResponseEntity.noContent().build();
    }
}