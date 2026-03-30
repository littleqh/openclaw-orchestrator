package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.LLMModelDetail;
import com.openclaw.orchestrator.dto.LLMModelRequest;
import com.openclaw.orchestrator.dto.LLMModelTestResponse;
import com.openclaw.orchestrator.entity.LLMModel;
import com.openclaw.orchestrator.repository.LLMModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMModelService {

    private final LLMModelRepository llmModelRepository;

    public List<LLMModelDetail> list() {
        return llmModelRepository.findAll().stream()
            .map(LLMModelDetail::fromEntity)
            .collect(Collectors.toList());
    }

    public List<LLMModelDetail> listEnabled() {
        return llmModelRepository.findByEnabledTrue().stream()
            .map(LLMModelDetail::fromEntity)
            .collect(Collectors.toList());
    }

    public LLMModelDetail getById(Long id) {
        return llmModelRepository.findById(id)
            .map(LLMModelDetail::fromEntity)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:模型不存在"));
    }

    @Transactional
    public LLMModelDetail create(LLMModelRequest request) {
        if (llmModelRepository.existsByName(request.getName())) {
            throw new RuntimeException("CONFLICT:模型名称已存在");
        }
        LLMModel model = LLMModel.builder()
            .name(request.getName())
            .provider(request.getProvider())
            .model(request.getModel())
            .baseUrl(request.getBaseUrl())
            .apiKey(request.getApiKey())
            .maxTokens(request.getMaxTokens())
            .enabled(request.getEnabled() != null ? request.getEnabled() : true)
            .apiFormat(request.getApiFormat() != null ? request.getApiFormat() : "openai")
            .build();
        return LLMModelDetail.fromEntity(llmModelRepository.save(model));
    }

    @Transactional
    public LLMModelDetail update(Long id, LLMModelRequest request) {
        LLMModel model = llmModelRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:模型不存在"));
        model.setName(request.getName());
        model.setProvider(request.getProvider());
        model.setModel(request.getModel());
        model.setBaseUrl(request.getBaseUrl());
        model.setApiKey(request.getApiKey());
        model.setMaxTokens(request.getMaxTokens());
        if (request.getEnabled() != null) {
            model.setEnabled(request.getEnabled());
        }
        if (request.getApiFormat() != null) {
            model.setApiFormat(request.getApiFormat());
        }
        return LLMModelDetail.fromEntity(llmModelRepository.save(model));
    }

    @Transactional
    public void delete(Long id) {
        LLMModel model = llmModelRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:模型不存在"));
        if (!model.getWorkers().isEmpty()) {
            throw new RuntimeException("CONFLICT:该模型已被 Worker 引用，无法删除");
        }
        llmModelRepository.delete(model);
    }

    public LLMModelTestResponse testConnection(Long id) {
        LLMModel model = llmModelRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:模型不存在"));

        long start = System.currentTimeMillis();
        try {
            WebClient client = WebClient.builder()
                .baseUrl(model.getBaseUrl().replaceFirst("/$", ""))
                .defaultHeader("Content-Type", "application/json")
                .build();

            String requestBody = buildTestRequestBody(model);

            String response = client.post()
                .uri(getEndpoint(model.getProvider(), model.getApiFormat()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.error("LLM connection test failed for model {}: {}", model.getName(), e.getMessage());
                    return Mono.just("{\"error\": \"" + e.getMessage() + "\"}");
                })
                .block();

            long latency = System.currentTimeMillis() - start;

            if (response != null && response.contains("error")) {
                return LLMModelTestResponse.builder()
                    .ok(false)
                    .error(response)
                    .latencyMs(latency)
                    .build();
            }

            return LLMModelTestResponse.builder()
                .ok(true)
                .latencyMs(latency)
                .build();

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("LLM connection test failed for model {}", model.getName(), e);
            return LLMModelTestResponse.builder()
                .ok(false)
                .error(e.getMessage())
                .latencyMs(latency)
                .build();
        }
    }

    private String getEndpoint(String provider, String apiFormat) {
        if ("ollama".equalsIgnoreCase(provider)) {
            return "/api/chat";
        }
        if ("anthropic".equalsIgnoreCase(apiFormat)) {
            // MiniMax Anthropic 兼容格式
            return "/v1/text/chatcompletion_v2";
        }
        return "/v1/chat/completions";
    }

    private String buildTestRequestBody(LLMModel model) {
        return switch (model.getProvider().toLowerCase()) {
            case "ollama" -> """
                {
                    "model": "%s",
                    "messages": [{"role": "user", "content": "hi"}],
                    "stream": false
                }
                """.formatted(model.getModel());
            default -> """
                {
                    "model": "%s",
                    "messages": [{"role": "user", "content": "hi"}],
                    "max_tokens": 10
                }
                """.formatted(model.getModel());
        };
    }
}
