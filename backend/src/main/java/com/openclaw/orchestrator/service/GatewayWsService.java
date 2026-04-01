package com.openclaw.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.orchestrator.entity.Worker;
import com.openclaw.orchestrator.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gateway WebSocket 服务 - 通过 WebSocket 协议与 Gateway 通信
 *
 * Gateway 是 WebSocket 服务器，不支持 HTTP 调用。
 * 此服务处理:
 * 1. WebSocket 连接建立
 * 2. 设备认证 (Ed25519 签名)
 * 3. 工具调用 (tools.invoke)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayWsService {

    private final WorkerRepository workerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 协议常量 ==========
    private static final int PROTOCOL_VERSION = 3;
    private static final String DEFAULT_CLIENT_ID = "gateway-client";
    private static final String DEFAULT_CLIENT_MODE = "backend";
    private static final String DEFAULT_CLIENT_VERSION = "1.0.0";
    private static final String DEFAULT_PLATFORM = "linux";
    private static final int CONNECT_TIMEOUT_SECONDS = 15;

    /**
     * 通过 WebSocket 调用 Gateway 工具方法
     *
     * @param worker Worker 实体
     * @param method 工具方法名 (如 "logs.tail", "status")
     * @param args 参数
     * @return 响应 JSON
     */
    public JsonNode invokeTool(Worker worker, String method, Map<String, Object> args) {
        String gatewayUrl = worker.getGatewayUrl();
        String gatewayToken = worker.getGatewayToken();

        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new RuntimeException("Gateway URL not configured");
        }

        try {
            // 1. 初始化设备密钥
            DeviceKeys deviceKeys = initializeDeviceKeys(worker);

            // 2. 建立 WebSocket 连接并认证
            URI uri = new URI(gatewayUrl);
            String result = executeWsCall(uri, gatewayToken, deviceKeys, method, args);

            // 3. 解析响应
            return objectMapper.readTree(result);

        } catch (Exception e) {
            log.error("[GatewayWsService] invokeTool error: method={}, error={}", method, e.getMessage());
            try {
                return objectMapper.readTree("{\"ok\":false,\"error\":{\"message\":\"" + e.getMessage() + "\"}}");
            } catch (Exception ex) {
                return objectMapper.createObjectNode();
            }
        }
    }

    /**
     * 通过 HTTP 调用 Gateway 工具方法 (用于 WebSocket 不支持的工具如 sessions_send)
     */
    public JsonNode invokeToolHttp(Worker worker, String tool, Map<String, Object> args) {
        String gatewayUrl = worker.getGatewayUrl();
        String gatewayToken = worker.getGatewayToken();

        log.info("[GatewayWsService] invokeToolHttp: gatewayUrl={}, token={}, tool={}, args={}",
                gatewayUrl, gatewayToken != null ? "***" : "null", tool, args);

        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new RuntimeException("Gateway URL not configured");
        }

        try {
            // 尝试不同的 HTTP 端点
            String baseUrl = gatewayUrl.replace("ws://", "http://").replace("wss://", "https://");
            // 移除末尾斜杠避免双重斜杠
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String[] endpoints = {"/tools/invoke", "/api/tools/invoke"};
            String result = null;

            for (String ep : endpoints) {
                String httpUrl = baseUrl + ep;
                log.info("[GatewayWsService] Trying HTTP URL: {}", httpUrl);

                try {
                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    if (gatewayToken != null && !gatewayToken.isBlank()) {
                        headers.set("Authorization", "Bearer " + gatewayToken);
                    }

                    Map<String, Object> body = new HashMap<>();
                    body.put("tool", tool);
                    body.put("args", args != null ? args : Map.of());

                    String requestBody = objectMapper.writeValueAsString(body);
                    log.info("[GatewayWsService] HTTP request body: {}", requestBody);

                    org.springframework.web.reactive.function.client.WebClient webClient =
                            org.springframework.web.reactive.function.client.WebClient.builder()
                                    .defaultHeaders(h -> h.addAll(headers))
                                    .build();

                    result = webClient.post()
                            .uri(httpUrl)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofSeconds(30));

                    log.info("[GatewayWsService] HTTP response from {}: {}", httpUrl, result);
                    if (result != null) {
                        return objectMapper.readTree(result);
                    }
                } catch (Exception e) {
                    log.warn("[GatewayWsService] HTTP endpoint {} failed: {} - class: {}",
                            httpUrl, e.getMessage(), e.getClass().getSimpleName());
                    // 如果是 4xx 错误（但不是 404），直接返回错误
                    if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
                        int status = wcre.getStatusCode().value();
                        if (status >= 400 && status < 500 && status != 404) {
                            log.error("[GatewayWsService] HTTP {} error: {}", status, wcre.getResponseBodyAsString());
                            return objectMapper.readTree(wcre.getResponseBodyAsString());
                        }
                    }
                }
            }

            // 所有端点都失败
            return objectMapper.readTree("{\"ok\":false,\"error\":{\"message\":\"All HTTP endpoints failed\"}}");

        } catch (Exception e) {
            log.error("[GatewayWsService] invokeToolHttp error: tool={}, error={}", tool, e.getMessage(), e);
            try {
                return objectMapper.readTree("{\"ok\":false,\"error\":{\"message\":\"" + e.getMessage() + "\"}}");
            } catch (Exception ex) {
                return objectMapper.createObjectNode();
            }
        }
    }

    /**
     * 设备密钥持有类
     */
    private static class DeviceKeys {
        final PrivateKey privateKey;
        final String publicKey;
        final byte[] publicKeyRaw;

        DeviceKeys(PrivateKey privateKey, String publicKey, byte[] publicKeyRaw) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.publicKeyRaw = publicKeyRaw;
        }
    }

    /**
     * 执行 WebSocket 调用
     */
    private String executeWsCall(URI uri, String token, DeviceKeys deviceKeys, String method, Map<String, Object> args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        CountDownLatch challengeLatch = new CountDownLatch(1);

        org.springframework.web.reactive.socket.client.WebSocketClient client =
                new org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.add("Authorization", "Bearer " + token);
        }
        headers.add("Origin", "http://localhost");

        String requestId = UUID.randomUUID().toString();
        String deviceId = generateDeviceId(deviceKeys);

        // 直接方法调用请求帧
        Map<String, Object> toolRequest = new HashMap<>();
        toolRequest.put("type", "req");
        toolRequest.put("id", requestId);
        toolRequest.put("method", method);
        toolRequest.put("params", args != null ? args : Map.of());

        String toolRequestJson = objectMapper.writeValueAsString(toolRequest);
        log.debug("[GatewayWsService] Sending request: {}", toolRequestJson);

        return Mono.fromCallable(() -> {
            try {
                client.execute(
                        uri,
                        headers,
                        webSocketSession -> {
                            log.info("[GatewayWsService] WebSocket session opened: {}", webSocketSession.getId());

                            // 首先发送 connect 认证
                            return webSocketSession.receive()
                                    .doOnNext(message -> {
                                        String text = message.getPayloadAsText();
                                        log.info("[GatewayWsService] Received: {}", text);

                                        try {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> response = objectMapper.readValue(text, Map.class);
                                            String type = (String) response.get("type");

                                            if ("event".equals(type)) {
                                                String event = (String) response.get("event");
                                                if ("connect.challenge".equals(event)) {
                                                    // 收到挑战，发送 connect 响应
                                                    @SuppressWarnings("unchecked")
                                                    Map<String, Object> payload = (Map<String, Object>) response.get("payload");
                                                    String nonce = payload != null ? (String) payload.get("nonce") : null;

                                                    if (nonce != null) {
                                                        challengeLatch.countDown();

                                                        try {
                                                            String connectJson = buildConnectParams(deviceId, token, nonce, deviceKeys);
                                                            log.info("[GatewayWsService] Sending connect: {}", connectJson);
                                                            webSocketSession.send(Mono.just(webSocketSession.textMessage(connectJson)))
                                                                    .subscribe();
                                                        } catch (Exception e) {
                                                            log.error("[GatewayWsService] Build connect params error", e);
                                                        }
                                                    }
                                                }
                                            } else if ("res".equals(type)) {
                                                // 根据 requestId 判断是 connect 响应还是工具调用响应
                                                String resId = (String) response.get("id");
                                                Boolean ok = (Boolean) response.get("ok");

                                                if ("connect".equals(resId) || "0".equals(resId)) {
                                                    // connect 响应，发送工具调用
                                                    if (Boolean.TRUE.equals(ok)) {
                                                        log.info("[GatewayWsService] Connected successfully, sending tool request");
                                                        webSocketSession.send(Mono.just(webSocketSession.textMessage(toolRequestJson)))
                                                                .subscribe();
                                                    } else {
                                                        @SuppressWarnings("unchecked")
                                                        Map<String, Object> error = (Map<String, Object>) response.get("error");
                                                        String errorMsg = error != null ? (String) error.get("message") : "Unknown error";
                                                        log.error("[GatewayWsService] Connect error: {}", errorMsg);
                                                        resultRef.set("{\"ok\":false,\"error\":{\"message\":\"" + errorMsg + "\"}}");
                                                        latch.countDown();
                                                        webSocketSession.close().subscribe();
                                                    }
                                                } else if (requestId.equals(resId)) {
                                                    // 工具调用响应
                                                    log.debug("[GatewayWsService] Received tool response: {}", text);
                                                    resultRef.set(text);
                                                    latch.countDown();
                                                    webSocketSession.close().subscribe();
                                                }
                                            }
                                        } catch (Exception e) {
                                            log.error("[GatewayWsService] Error processing message", e);
                                        }
                                    })
                                    .doOnError(err -> {
                                        log.error("[GatewayWsService] WebSocket error: {}", err.getMessage());
                                        resultRef.set("{\"ok\":false,\"error\":{\"message\":\"" + err.getMessage() + "\"}}");
                                        latch.countDown();
                                    })
                                    .then();
                        }
                ).block(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS));
            } catch (Exception e) {
                log.error("[GatewayWsService] WebSocket execute error", e);
                resultRef.set("{\"ok\":false,\"error\":{\"message\":\"" + e.getMessage() + "\"}}");
                latch.countDown();
            }

            // 等待挑战或超时
            if (!challengeLatch.await(5, TimeUnit.SECONDS)) {
                log.warn("[GatewayWsService] Challenge timeout");
            }

            // 等待最终结果或超时
            if (!latch.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("[GatewayWsService] Response timeout");
            }

            String result = resultRef.get();
            if (result == null) {
                result = "{\"ok\":false,\"error\":{\"message\":\"No response\"}}";
            }
            return result;

        }).subscribeOn(Schedulers.boundedElastic())
                .block(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS + 5));
    }

    /**
     * 初始化设备密钥
     */
    private DeviceKeys initializeDeviceKeys(Worker worker) throws Exception {
        if (worker.getDevicePrivateKey() != null && !worker.getDevicePrivateKey().isEmpty()) {
            log.info("[GatewayWsService] Loading existing device keys");
            PrivateKey privateKey = loadPrivateKeyFromPem(worker.getDevicePrivateKey());
            String publicKey = worker.getDevicePublicKey();
            byte[] publicKeyRaw = Base64.getUrlDecoder().decode(publicKey);
            return new DeviceKeys(privateKey, publicKey, publicKeyRaw);
        }

        throw new RuntimeException("Device keys not found. Please pair the worker first.");
    }

    /**
     * 从 PEM 格式加载私钥
     */
    private PrivateKey loadPrivateKeyFromPem(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(base64);
        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    /**
     * 生成设备 ID
     */
    private String generateDeviceId(DeviceKeys keys) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keys.publicKeyRaw);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "device-" + System.currentTimeMillis();
        }
    }

    /**
     * 对 payload 进行 Ed25519 签名
     */
    private String signPayload(String payload, DeviceKeys keys) throws Exception {
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keys.privateKey);
        sig.update(payload.getBytes(StandardCharsets.UTF_8));
        byte[] rawSignature = sig.sign();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);
    }

    /**
     * 构建设备认证 Connect 请求
     */
    private String buildConnectParams(String deviceId, String token, String nonce, DeviceKeys keys) throws Exception {
        long signedAtMs = System.currentTimeMillis();

        String devicePayload = String.join("|",
                "v3",
                deviceId != null ? deviceId : "",
                DEFAULT_CLIENT_ID,
                DEFAULT_CLIENT_MODE,
                "operator",
                "operator.admin",
                String.valueOf(signedAtMs),
                token != null ? token : "",
                nonce != null ? nonce : "",
                DEFAULT_PLATFORM.toLowerCase(),
                ""
        );

        String signature = signPayload(devicePayload, keys);

        Map<String, Object> deviceMap = new HashMap<>();
        deviceMap.put("id", deviceId);
        deviceMap.put("publicKey", keys.publicKey);
        deviceMap.put("signature", signature);
        deviceMap.put("signedAt", signedAtMs);
        if (nonce != null && !nonce.isEmpty()) {
            deviceMap.put("nonce", nonce);
        }

        Map<String, Object> connectParams = new HashMap<>();
        connectParams.put("minProtocol", PROTOCOL_VERSION);
        connectParams.put("maxProtocol", PROTOCOL_VERSION);
        connectParams.put("client", Map.of(
                "id", DEFAULT_CLIENT_ID,
                "version", DEFAULT_CLIENT_VERSION,
                "mode", DEFAULT_CLIENT_MODE,
                "platform", DEFAULT_PLATFORM
        ));
        connectParams.put("role", "operator");
        connectParams.put("scopes", List.of("operator.admin"));
        connectParams.put("auth", Map.of("token", token != null ? token : ""));
        connectParams.put("device", deviceMap);

        // connect 请求的 id 设为固定值，用于识别
        Map<String, Object> requestFrame = new HashMap<>();
        requestFrame.put("type", "req");
        requestFrame.put("id", "connect");
        requestFrame.put("method", "connect");
        requestFrame.put("params", connectParams);

        return objectMapper.writeValueAsString(requestFrame);
    }
}
