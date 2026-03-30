package com.openclaw.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.orchestrator.entity.LLMModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import com.openclaw.orchestrator.dto.LLMChunk;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMClientService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Send a chat request and return a streaming response.
     * Returns flux of LLMChunk (content + thinking).
     */
    public Flux<LLMChunk> streamChat(LLMModel model, List<Map<String, String>> messages) {
        String baseUrl = model.getBaseUrl().replaceFirst("/$", "");
        String endpoint = getEndpoint(model.getProvider(), model.getApiFormat());
        String fullUrl = baseUrl + endpoint;

        log.info("[LLMClient] Calling provider={}, model={}, url={}", model.getProvider(), model.getModel(), fullUrl);
        log.info("[LLMClient] API Key present: {}, value: {}", model.getApiKey() != null && !model.getApiKey().isEmpty(), model.getApiKey() != null ? model.getApiKey().substring(0, Math.min(4, model.getApiKey().length())) + "****" : "null");

        WebClient.Builder clientBuilder = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json");

        if (model.getApiKey() != null && !model.getApiKey().isEmpty()) {
            String maskedKey = model.getApiKey().substring(0, Math.min(4, model.getApiKey().length())) + "****";
            log.info("[LLMClient] Using API key: {}", maskedKey);
            clientBuilder.defaultHeader("Authorization", "Bearer " + model.getApiKey());
        }

        WebClient client = clientBuilder.build();

        Map<String, Object> requestBody = buildRequestBody(model, messages);
        log.info("[LLMClient] Request body: {}", requestBody);

        log.info("[LLMClient] Calling {} with model {}", model.getProvider(), model.getModel());

        // For streaming, we use exchangeToFlux which handles SSE properly
        return client.post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(String.class)
            .doOnNext(line -> log.info("[LLMClient] Raw received: {}", line))
            .filter(line -> line != null && !line.isEmpty())
            .flatMap(line -> processLine(line, model.getProvider()))
            .doOnError(e -> log.error("[LLMClient] Stream error: {}", e.getMessage(), e));
    }

    private Flux<LLMChunk> processLine(String line, String provider) {
        try {
            if ("ollama".equalsIgnoreCase(provider)) {
                return processOllamaLine(line);
            } else {
                return processOpenAILine(line);
            }
        } catch (Exception e) {
            log.error("Failed to process line: {}", line, e);
            return Flux.empty();
        }
    }

    private Flux<LLMChunk> processOllamaLine(String line) {
        try {
            if (line.startsWith("{")) {
                JsonNode node = objectMapper.readTree(line);
                if (node.has("message") && node.get("message").has("content")) {
                    String content = node.get("message").get("content").asText();
                    if (content != null && !content.isEmpty()) {
                        return Flux.just(new LLMChunk(content, null));
                    }
                }
                if (node.has("done") && node.get("done").asBoolean()) {
                    return Flux.just(new LLMChunk("[DONE]", null));
                }
            }
            return Flux.empty();
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    private Flux<LLMChunk> processOpenAILine(String line) {
        try {
            // MiniMax sends raw JSON without "data:" prefix
            String jsonStr = line.startsWith("data:") ? line.substring(5).trim() : line;
            if ("[DONE]".equals(jsonStr)) {
                return Flux.just(new LLMChunk("[DONE]", null));
            }
            JsonNode node = objectMapper.readTree(jsonStr);
            if (node.has("choices") && node.get("choices").isArray()) {
                for (JsonNode choice : node.get("choices")) {
                    if (choice.has("delta")) {
                        JsonNode delta = choice.get("delta");
                        String content = null;
                        String thinking = null;
                        // Standard content field
                        if (delta.has("content")) {
                            String c = delta.get("content").asText();
                            if (c != null && !c.isEmpty()) {
                                content = c;
                            }
                        }
                        // MiniMax reasoning_content = thinking process
                        if (delta.has("reasoning_content")) {
                            String tc = delta.get("reasoning_content").asText();
                            if (tc != null && !tc.isEmpty()) {
                                thinking = tc;
                            }
                        }
                        if (content != null || thinking != null) {
                            return Flux.just(new LLMChunk(content, thinking));
                        }
                    }
                }
            }
            return Flux.empty();
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    /**
     * Send a chat request and return a complete response (non-streaming).
     */
    public String chat(LLMModel model, List<Map<String, String>> messages) {
        String baseUrl = model.getBaseUrl().replaceFirst("/$", "");
        String endpoint = getEndpoint(model.getProvider(), model.getApiFormat());

        WebClient.Builder clientBuilder = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json");

        if (model.getApiKey() != null && !model.getApiKey().isEmpty()) {
            clientBuilder.defaultHeader("Authorization", "Bearer " + model.getApiKey());
        }

        WebClient client = clientBuilder.build();

        Map<String, Object> requestBody = buildRequestBody(model, messages);
        requestBody = new java.util.HashMap<>(requestBody);
        requestBody.put("stream", false);

        String response = client.post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return extractContentFromResponse(response, model.getProvider());
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

    private Map<String, Object> buildRequestBody(LLMModel model, List<Map<String, String>> messages) {
        if ("ollama".equalsIgnoreCase(model.getProvider())) {
            return Map.of(
                "model", model.getModel(),
                "messages", messages,
                "stream", true
            );
        } else {
            return Map.of(
                "model", model.getModel(),
                "messages", messages,
                "max_tokens", model.getMaxTokens() != null ? model.getMaxTokens() : 4096,
                "stream", true
            );
        }
    }

    private String extractContentFromResponse(String response, String provider) {
        try {
            JsonNode node = objectMapper.readTree(response);
            if ("ollama".equalsIgnoreCase(provider)) {
                if (node.has("message") && node.get("message").has("content")) {
                    return node.get("message").get("content").asText();
                }
            } else {
                if (node.has("choices") && node.get("choices").isArray()) {
                    for (JsonNode choice : node.get("choices")) {
                        if (choice.has("message") && choice.get("message").has("content")) {
                            return choice.get("message").get("content").asText();
                        }
                    }
                }
            }
            return response;
        } catch (Exception e) {
            return response;
        }
    }
}
