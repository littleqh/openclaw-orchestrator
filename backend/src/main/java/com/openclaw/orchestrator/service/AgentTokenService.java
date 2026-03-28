package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.TokenResponse;
import com.openclaw.orchestrator.entity.AgentToken;
import com.openclaw.orchestrator.entity.TokenAccessLog;
import com.openclaw.orchestrator.entity.Worker;
import com.openclaw.orchestrator.repository.AgentTokenRepository;
import com.openclaw.orchestrator.repository.TokenAccessLogRepository;
import com.openclaw.orchestrator.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentTokenService {

    private final AgentTokenRepository agentTokenRepository;
    private final TokenAccessLogRepository tokenAccessLogRepository;
    private final WorkerRepository workerRepository;

    @Transactional
    public String createSystemToken() {
        if (agentTokenRepository.findSystemToken().isPresent()) {
            throw new RuntimeException("CONFLICT:System token already exists");
        }
        String token = UUID.randomUUID().toString();
        AgentToken agentToken = AgentToken.builder()
                .token(token)
                .worker(null)
                .build();
        agentTokenRepository.save(agentToken);
        return token;
    }

    @Transactional
    public String createAgentToken(Long workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Worker not found"));

        if (agentTokenRepository.existsByWorkerId(workerId)) {
            throw new RuntimeException("CONFLICT:Token for this worker already exists");
        }

        String token = UUID.randomUUID().toString();
        AgentToken agentToken = AgentToken.builder()
                .token(token)
                .worker(worker)
                .build();
        agentTokenRepository.save(agentToken);
        return token;
    }

    public TokenResponse createSystemTokenResponse() {
        String token = createSystemToken();
        List<AgentToken> all = agentTokenRepository.findAll();
        AgentToken created = all.stream()
                .filter(t -> t.getToken() != null && t.getToken().equals(token))
                .findFirst()
                .orElseThrow();
        return toResponse(created, true);
    }

    public TokenResponse createAgentTokenResponse(Long workerId) {
        System.out.println("=== createAgentTokenResponse ===");
        System.out.println("workerId: " + workerId);
        String token = createAgentToken(workerId);
        System.out.println("created token: " + token);
        AgentToken created = agentTokenRepository.findByToken(token).orElseThrow();
        System.out.println("found created token: " + (created != null ? "id=" + created.getId() : "null"));
        return toResponse(created, true);
    }

    @Transactional
    public String resetToken(Long id) {
        AgentToken agentToken = agentTokenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Token not found"));

        String newToken = UUID.randomUUID().toString();
        agentToken.setToken(newToken);
        agentToken.setLastAccessAt(LocalDateTime.now());
        agentTokenRepository.save(agentToken);
        return newToken;
    }

    public TokenResponse resetTokenResponse(Long id) {
        resetToken(id);
        return getTokenInfo(id);
    }

    @Transactional
    public void deleteToken(Long id) {
        if (!agentTokenRepository.existsById(id)) {
            throw new RuntimeException("NOT_FOUND:Token not found");
        }
        agentTokenRepository.deleteById(id);
    }

    public TokenResponse getTokenInfo(Long id) {
        AgentToken agentToken = agentTokenRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Token not found"));
        System.out.println("=== getTokenInfo ===");
        System.out.println("id: " + id);
        System.out.println("agentToken.token: " + agentToken.getToken());
        System.out.println("agentToken.worker: " + agentToken.getWorker());
        TokenResponse response = toResponse(agentToken, true);
        System.out.println("response.token: " + response.getToken());
        return response;
    }

    public List<TokenResponse> getAllTokens() {
        System.out.println("=== getAllTokens ===");
        List<AgentToken> tokens = agentTokenRepository.findAllAgentTokens();
        System.out.println("agent tokens count: " + tokens.size());
        for (AgentToken at : tokens) {
            System.out.println("  - id: " + at.getId() + ", worker: " + (at.getWorker() != null ? at.getWorker().getId() : "null"));
        }
        AgentToken systemToken = agentTokenRepository.findSystemToken().orElse(null);
        System.out.println("system token: " + (systemToken != null ? systemToken.getId() : "null"));

        List<TokenResponse> responses = tokens.stream()
                .map(t -> toResponse(t, false))
                .collect(Collectors.toList());

        if (systemToken != null) {
            responses.add(0, toResponse(systemToken, false));
        }
        System.out.println("total responses: " + responses.size());
        return responses;
    }

    public AgentToken validateToken(String token) {
        return agentTokenRepository.findByToken(token).orElse(null);
    }

    @Transactional
    public void logAccess(AgentToken agentToken, String path, String ip, boolean success) {
        TokenAccessLog log = TokenAccessLog.builder()
                .agentToken(agentToken)
                .accessPath(path)
                .accessIp(ip)
                .success(success)
                .build();
        tokenAccessLogRepository.save(log);

        if (success) {
            agentToken.setLastAccessAt(LocalDateTime.now());
            agentTokenRepository.save(agentToken);
        }
    }

    public TokenResponse toResponse(AgentToken agentToken, boolean includeFullToken) {
        String tokenPreview = null;
        if (agentToken.getToken() != null && agentToken.getToken().length() > 4) {
            tokenPreview = "****-****-****-" + agentToken.getToken().substring(agentToken.getToken().length() - 4);
        }

        return TokenResponse.builder()
                .id(agentToken.getId())
                .token(includeFullToken ? agentToken.getToken() : null)
                .tokenPreview(tokenPreview)
                .type(agentToken.getWorker() == null ? "SYSTEM" : "AGENT")
                .workerId(agentToken.getWorker() != null ? agentToken.getWorker().getId() : null)
                .workerName(agentToken.getWorker() != null ? agentToken.getWorker().getName() : null)
                .createdAt(agentToken.getCreatedAt())
                .lastAccessAt(agentToken.getLastAccessAt())
                .build();
    }
}