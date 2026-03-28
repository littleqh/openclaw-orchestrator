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
        TokenResponse created;
        if (request == null || request.getWorkerId() == null) {
            created = agentTokenService.createSystemTokenResponse();
        } else {
            created = agentTokenService.createAgentTokenResponse(request.getWorkerId());
        }
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TokenResponse> get(@PathVariable Long id) {
        System.out.println("=== AgentTokenController.get ===");
        System.out.println("id: " + id);
        TokenResponse response = agentTokenService.getTokenInfo(id);
        System.out.println("response.token: " + response.getToken());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reset")
    public ResponseEntity<TokenResponse> reset(@PathVariable Long id) {
        return ResponseEntity.ok(agentTokenService.resetTokenResponse(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        agentTokenService.deleteToken(id);
        return ResponseEntity.noContent().build();
    }
}