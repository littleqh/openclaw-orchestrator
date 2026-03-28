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
        return toResponse(agentToken, false);
    }

    public List<TokenResponse> getAllTokens() {
        List<AgentToken> tokens = agentTokenRepository.findAllAgentTokens();
        AgentToken systemToken = agentTokenRepository.findSystemToken().orElse(null);

        List<TokenResponse> responses = tokens.stream()
                .map(t -> toResponse(t, false))
                .collect(Collectors.toList());

        if (systemToken != null) {
            responses.add(0, toResponse(systemToken, false));
        }
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