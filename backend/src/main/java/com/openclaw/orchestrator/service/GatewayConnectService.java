package com.openclaw.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.orchestrator.dto.GatewayConnectResult;
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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;
import java.util.Base64;

/**
 * Gateway 连接服务 - 负责与 OpenClaw Gateway 建立 WebSocket 连接并进行设备认证
 *
 * 认证流程概述:
 * 1. 尝试从 Worker 实体加载已保存的设备密钥
 * 2. 如果没有保存的密钥,则生成新的 Ed25519 密钥对
 * 3. 建立 WebSocket 连接
 * 4. 收到 Gateway 发送的 connect.challenge 事件(包含 nonce)
 * 5. 使用私钥对设备认证 payload 进行签名
 * 6. 发送带有签名的 connect 请求
 * 7. 如果返回 PAIRING_REQUIRED,需要手动批准后重试
 * 8. 成功后将设备密钥保存到 Worker 实体
 *
 * @see <a href="../../../docs/superpowers/specs/2026-03-30-gateway-connection-authentication-design.md">认证流程详细设计文档</a>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GatewayConnectService {

    private final WorkerRepository workerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 协议常量 ==========

    /** 协议版本 v3 */
    private static final int PROTOCOL_VERSION = 3;

    /** 客户端标识符 - Gateway 用来识别客户端类型 */
    private static final String DEFAULT_CLIENT_ID = "gateway-client";

    /** 客户端模式 - backend 表示后端服务 */
    private static final String DEFAULT_CLIENT_MODE = "backend";

    /** 客户端版本号 */
    private static final String DEFAULT_CLIENT_VERSION = "1.0.0";

    /** 目标平台 */
    private static final String DEFAULT_PLATFORM = "linux";

    /** 连接超时时间(秒) */
    private static final int CONNECT_TIMEOUT_SECONDS = 15;

    // ========== 实例状态(非线程安全,每次连接重置) ==========

    /** Ed25519 私钥 - 用于对设备认证 payload 签名 */
    private PrivateKey devicePrivateKey;

    /** Ed25519 公钥(Base64URL 编码) - 发送给 Gateway */
    private String devicePublicKey;

    /** Ed25519 公钥原始字节 - 用于生成 Device ID */
    private byte[] devicePublicKeyRaw;

    /** 当前处理的 Worker ID */
    private String currentWorkerId;

    /**
     * 标记密钥是否从数据库加载
     * - true: 从 Worker 实体加载的,不需要重新保存
     * - false: 新生成的,需要保存到数据库
     */
    private boolean keysWereLoaded;

    /**
     * 主入口方法 - 发起 Gateway 连接
     *
     * @param workerId Worker 实体的 ID
     * @return GatewayConnectResult 包含连接状态、日志信息和错误码
     */
    public GatewayConnectResult connect(Long workerId) {
        log.info("=== GatewayConnectService.connect() 被调用, workerId={} ===", workerId);

        // 1. 查询 Worker 实体
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:员工不存在"));
        log.info("Worker 查询完成: {}", worker.getName());

        // 2. 检查是否为本地 Agent 模式(本地模式不需要 Gateway 连接)
        if (Boolean.TRUE.equals(worker.getLocalRuntime())) {
            return GatewayConnectResult.builder()
                    .status("error")
                    .message("本地 Agent 不需要连接 Gateway")
                    .errorCode("local_runtime")
                    .logs(List.of("错误: 本地 Agent 模式不支持 Gateway 连接"))
                    .build();
        }

        // 3. 获取 Gateway 配置
        String gatewayUrl = worker.getGatewayUrl();
        String gatewayToken = worker.getGatewayToken();

        // 4. 验证 Gateway URL
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            return GatewayConnectResult.builder()
                    .status("error")
                    .message("Gateway 地址未配置")
                    .errorCode("url_missing")
                    .logs(List.of("错误: Gateway 地址未配置，请在员工详情中配置 gatewayUrl"))
                    .build();
        }

        List<String> logs = new ArrayList<>();
        logs.add("正在连接到 " + gatewayUrl + "...");

        try {
            log.info("准备连接 gatewayUrl={}, token={}", gatewayUrl, gatewayToken != null ? "****" : "null");
            java.net.URI uri = new java.net.URI(gatewayUrl);
            String scheme = uri.getScheme();
            log.info("URI scheme={}", scheme);

            // 5. 验证协议类型(仅支持 ws:// 和 wss://)
            if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
                return GatewayConnectResult.builder()
                        .status("error")
                        .message("不支持的协议: " + scheme + "，仅支持 ws:// 或 wss://")
                        .errorCode("protocol_unsupported")
                        .logs(logs)
                        .build();
            }

            logs.add("协议验证通过");
            logs.add("正在初始化设备密钥...");

            // 6. 初始化设备密钥(从数据库加载或重新生成)
            devicePrivateKey = null;
            initializeDeviceKeys(worker);
            String deviceId = generateDeviceId();

            logs.add("设备密钥初始化完成，deviceId: " + deviceId.substring(0, 8) + "...");
            logs.add("正在建立 WebSocket 连接...");
            log.info("准备调用 performConnect, uri={}", uri);

            // 7. 执行 WebSocket 连接和认证
            GatewayConnectResult result = performConnect(uri, gatewayToken, deviceId, logs, worker);
            log.info("performConnect 返回, status={}", result.getStatus());

            // 8. 根据连接结果决定是否保存密钥
            // 只有新生成的密钥才需要保存,已加载的不需要重复保存
            if (!keysWereLoaded && ("connected".equals(result.getStatus()) || "pairing_required".equals(result.getStatus()))) {
                try {
                    saveDeviceKeysToWorker(worker);
                    logs.add("设备密钥已保存，下次连接将复用");
                } catch (Exception e) {
                    log.error("Failed to save device keys", e);
                }
            } else if (keysWereLoaded) {
                logs.add("设备密钥已加载（已保存过），无需重新保存");
            }

            return result;

        } catch (Exception e) {
            log.error("Gateway connection error for worker {}: {}", workerId, e.getMessage(), e);
            logs.add("连接失败: " + e.getMessage());
            return GatewayConnectResult.builder()
                    .status("error")
                    .message(e.getMessage())
                    .errorCode("connection_failed")
                    .logs(logs)
                    .build();
        }
    }

    /**
     * 初始化设备密钥
     *
     * 优先尝试从 Worker 实体加载已保存的密钥:
     * - 如果 Worker.devicePrivateKey 存在,则加载并使用已保存的密钥对
     * - 如果不存在,则生成新的 Ed25519 密钥对
     *
     * @param worker Worker 实体,用于存储/加载设备密钥
     * @throws Exception 如果密钥加载或生成失败
     */
    private void initializeDeviceKeys(Worker worker) throws Exception {
        keysWereLoaded = false;

        // 尝试从 Worker 实体加载已保存的设备密钥
        if (worker.getDevicePrivateKey() != null && !worker.getDevicePrivateKey().isEmpty()) {
            log.info("Loading existing device keys from Worker entity");
            devicePrivateKey = loadPrivateKeyFromPem(worker.getDevicePrivateKey());
            devicePublicKey = worker.getDevicePublicKey();
            // 从存储的 Base64URL 编码公钥解码为原始字节
            byte[] decodedPublicKey = Base64.getUrlDecoder().decode(devicePublicKey);
            devicePublicKeyRaw = decodedPublicKey;
            log.info("Loaded deviceId: {}", generateDeviceId());
            keysWereLoaded = true;
            return;
        }

        // 未找到保存的密钥,生成新的 Ed25519 密钥对
        log.info("Generating new device keys");
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = keyGen.generateKeyPair();
        devicePrivateKey = keyPair.getPrivate();

        // 从公钥的 SPKI 编码中提取原始 32 字节 Ed25519 公钥
        // Java 的 Ed25519 公钥编码格式: SEQUENCE { OID 1.3.101.110, BIT STRING (32 bytes) }
        // 标准 Ed25519 SPKI 长度通常为 44 字节,前 12 字节为前缀
        byte[] publicKeySpki = keyPair.getPublic().getEncoded();
        log.info("SPKI public key length: {}, hex: {}", publicKeySpki.length,
                bytesToHex(Arrays.copyOfRange(publicKeySpki, 0, Math.min(20, publicKeySpki.length))));
        devicePublicKeyRaw = extractEd25519Raw(publicKeySpki);
        log.info("Raw public key length: {}, hex: {}", devicePublicKeyRaw.length,
                bytesToHex(devicePublicKeyRaw));

        // Base64URL 编码公钥(不带 padding),用于传输和存储
        devicePublicKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(devicePublicKeyRaw);
        log.info("Generated deviceId: {}", generateDeviceId());
        keysWereLoaded = false;
    }

    /**
     * 从 PEM 格式字符串加载 Ed25519 私钥
     *
     * PEM 格式示例:
     * -----BEGIN PRIVATE KEY-----
     * MC4CAQAwBQYDK2VwBQIEQ...
     * -----END PRIVATE KEY-----
     *
     * @param pem 包含 PEM 头和尾的私钥字符串
     * @return PrivateKey 对象
     * @throws Exception 如果 PEM 解析或密钥构建失败
     */
    private PrivateKey loadPrivateKeyFromPem(String pem) throws Exception {
        // 移除 PEM 头和尾以及所有空白字符
        String base64 = pem;
        base64 = base64.replace("-----BEGIN PRIVATE KEY-----", "");
        base64 = base64.replace("-----END PRIVATE KEY-----", "");
        base64 = base64.replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(base64);
        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
        return keyFactory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(decoded));
    }

    /**
     * 将私钥转换为 PEM Base64 格式(无头)
     *
     * @param privateKey Ed25519 私钥
     * @return 无 PEM 头的 Base64 编码字符串
     */
    private String getPrivateKeyPem(PrivateKey privateKey) {
        byte[] encoded = privateKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        // 移除 PEM 头和尾以及所有空白字符
        base64 = base64.replace("-----BEGIN PRIVATE KEY-----", "");
        base64 = base64.replace("-----END PRIVATE KEY-----", "");
        base64 = base64.replaceAll("\\s", "");
        return base64;
    }

    /**
     * 将设备密钥保存到 Worker 实体
     *
     * 保存内容:
     * - deviceId: 公钥的 SHA-256 哈希(十六进制)
     * - devicePublicKey: Base64URL 编码的 32 字节公钥
     * - devicePrivateKey: 无 PEM 头的 Base64 编码私钥
     *
     * @param worker Worker 实体
     */
    private void saveDeviceKeysToWorker(Worker worker) {
        worker.setDeviceId(generateDeviceId());
        worker.setDevicePublicKey(devicePublicKey);
        worker.setDevicePrivateKey(getPrivateKeyPem(devicePrivateKey));
        workerRepository.save(worker);
        log.info("Device keys saved to Worker entity");
    }

    /**
     * 字节数组转十六进制字符串
     *
     * @param bytes 输入字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * 从 SPKI 编码的 Ed25519 公钥中提取原始 32 字节公钥
     *
     * Ed25519 公钥的 SPKI 编码格式:
     * 30 2a                           # SEQUENCE, length 42
     *   30 05                         # SEQUENCE, length 5 (algorithm identifier)
     *     06 03 2b 65 6e             # OID 1.3.101.110 (Ed25519)
     *   03 25 00                     # BIT STRING, length 37, with 0 unused bits
     *     <32 bytes raw public key>   # 实际的 32 字节 Ed25519 公钥
     *
     * @param spkiEncoded SPKI 编码的公钥
     * @return 32 字节原始 Ed25519 公钥
     */
    private byte[] extractEd25519Raw(byte[] spkiEncoded) {
        log.info("extractEd25519Raw called with length: {}", spkiEncoded.length);
        // 标准 Ed25519 SPKI 格式: 开头为 0x302a, 从第 12 字节开始是 32 字节公钥
        if (spkiEncoded.length == 44 && spkiEncoded[0] == 0x30 && spkiEncoded[1] == 0x2a) {
            byte[] result = Arrays.copyOfRange(spkiEncoded, 12, 44);
            log.info("Extracted from index 12, result length: {}", result.length);
            return result;
        }
        log.info("SPKI format not recognized, falling back to last 32 bytes");
        byte[] result = Arrays.copyOfRange(spkiEncoded, spkiEncoded.length - 32, spkiEncoded.length);
        log.info("Extracted last 32 bytes, result length: {}", result.length);
        return result;
    }

    /**
     * 生成设备唯一标识符
     *
     * Device ID 是公钥原始字节的 SHA-256 哈希,
     * 以 64 字符十六进制字符串形式表示.
     * Gateway 使用此 ID 来标识和追踪设备.
     *
     * @return 设备唯一标识符(64 字符十六进制)
     */
    private String generateDeviceId() {
        try {
            // 对原始公钥字节进行 SHA-256 哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(devicePublicKeyRaw);
            // Gateway 期望十六进制格式(64 字符),不是 base64
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("Error generating deviceId", e);
            return "device-" + System.currentTimeMillis();
        }
    }

    /**
     * 对设备认证 payload 进行 Ed25519 签名
     *
     * 签名过程:
     * 1. 将 payload 转为 UTF-8 字节
     * 2. 使用 Ed25519 私钥初始化签名器
     * 3. 计算签名得到 64 字节原始签名
     * 4. 使用 Base64URL 编码(无 padding)
     *
     * @param payload 待签名的设备认证字符串
     * @return Base64URL 编码的签名
     * @throws Exception 如果签名失败
     */
    private String signPayload(String payload) throws Exception {
        log.info("Signing payload (length={}): {}", payload.length(), payload.substring(0, Math.min(100, payload.length())));
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(devicePrivateKey);
        sig.update(payload.getBytes(StandardCharsets.UTF_8));
        byte[] rawSignature = sig.sign();
        log.info("Raw signature length: {} (expected 64 for Ed25519)", rawSignature.length);
        // Ed25519 签名输出为原始 64 字节,不是 DER 编码
        String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);
        log.info("Generated signature: {}", signatureB64);
        return signatureB64;
    }

    /**
     * 执行 WebSocket 连接和 Gateway 认证
     *
     * 完整流程:
     * 1. 建立 WebSocket 连接
     * 2. 等待并接收 connect.challenge 事件(包含 nonce)
     * 3. 使用 nonce 构建带签名的 connect 请求
     * 4. 发送 connect 请求并等待响应
     * 5. 处理响应(hello-ok 或错误)
     * 6. 关闭 WebSocket 连接
     *
     * @param uri Gateway WebSocket 地址
     * @param token Gateway 访问令牌
     * @param deviceId 设备唯一标识符
     * @param logs 日志列表(用于返回给调用方)
     * @param worker Worker 实体
     * @return GatewayConnectResult 连接结果
     */
    private GatewayConnectResult performConnect(java.net.URI uri, String token, String deviceId, List<String> logs, Worker worker) {
        log.info("=== performConnect 开始, uri={}, token={} ===", uri, token != null ? "****" : "null");
        CountDownLatch resultLatch = new CountDownLatch(1);
        AtomicReference<GatewayConnectResult> resultRef = new AtomicReference<>();
        List<String> connectionLogs = new ArrayList<>(logs);

        // 使用 Spring WebFlux 的 ReactorNettyWebSocketClient
        org.springframework.web.reactive.socket.client.WebSocketClient client =
                new org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient();

        // 构建 WebSocket 请求头
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.add("Authorization", "Bearer " + token);
        }
        headers.add("Origin", "http://localhost");

        String finalUri = uri.toString();
        log.info("WebSocket 连接 URI: {}, headers: Authorization={}", finalUri, token != null ? "****" : "null");

        // 在 BoundedElastic 线程池中执行,避免阻塞 Netty I/O 线程
        return Mono.fromCallable(() -> {
            try {
                client.execute(
                        URI.create(finalUri),
                        headers,
                        webSocketSession -> {
                            log.info("WebSocket 连接已建立, sessionId={}", webSocketSession.getId());
                            connectionLogs.add("WebSocket 连接已建立 (session=" + webSocketSession.getId() + ")");

                            // 使用 AtomicReference 存储接收到的 nonce
                            AtomicReference<String> nonceRef = new AtomicReference<>();
                            CountDownLatch challengeLatch = new CountDownLatch(1);

                            // 接收并处理 WebSocket 消息
                            return webSocketSession.receive()
                                    .doOnNext(message -> {
                                        String text = message.getPayloadAsText();
                                        log.info("收到消息: {}", text);
                                        connectionLogs.add("收到响应: " + text);

                                        try {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> response = objectMapper.readValue(text, Map.class);
                                            String type = (String) response.get("type");

                                            // 处理事件消息
                                            if ("event".equals(type)) {
                                                String event = (String) response.get("event");
                                                // Gateway 发送的挑战事件,包含一次性 nonce
                                                if ("connect.challenge".equals(event)) {
                                                    @SuppressWarnings("unchecked")
                                                    Map<String, Object> payload = (Map<String, Object>) response.get("payload");
                                                    String nonce = payload != null ? (String) payload.get("nonce") : null;
                                                    if (nonce != null) {
                                                        connectionLogs.add("收到连接挑战，nonce: " + nonce);
                                                        nonceRef.set(nonce);
                                                        challengeLatch.countDown();

                                                        // 收到挑战后,使用 nonce 构建并发送带签名的 connect 请求
                                                        try {
                                                            String json = buildSecondConnectParams(deviceId, token, nonce);
                                                            connectionLogs.add("发送 connect: " + json);
                                                            // 在 boundedElastic 线程中执行阻塞的 send 操作
                                                            Mono.fromCallable(() -> {
                                                                        webSocketSession.send(Mono.just(webSocketSession.textMessage(json))).block();
                                                                        return Mono.empty();
                                                                    })
                                                                    .subscribeOn(Schedulers.boundedElastic())
                                                                    .subscribe();
                                                        } catch (Exception e) {
                                                            log.error("发送 connect 失败", e);
                                                            connectionLogs.add("发送失败: " + e.getMessage());
                                                            resultRef.set(GatewayConnectResult.builder()
                                                                    .status("error")
                                                                    .message(e.getMessage())
                                                                    .errorCode("send_error")
                                                                    .logs(new ArrayList<>(connectionLogs))
                                                                    .build());
                                                            resultLatch.countDown();
                                                        }
                                                    }
                                                }
                                            } else if ("res".equals(type)) {
                                                // 处理 connect 响应
                                                Boolean ok = (Boolean) response.get("ok");

                                                if (Boolean.TRUE.equals(ok)) {
                                                    // 连接成功
                                                    connectionLogs.add("连接成功!");
                                                    resultRef.set(GatewayConnectResult.builder()
                                                            .status("connected")
                                                            .message("连接成功")
                                                            .logs(new ArrayList<>(connectionLogs))
                                                            .build());
                                                    // 正确关闭 WebSocket session
                                                    webSocketSession.close().subscribe();
                                                } else {
                                                    // 连接失败
                                                    @SuppressWarnings("unchecked")
                                                    Map<String, Object> error = (Map<String, Object>) response.get("error");
                                                    String errorMsg = error != null ? (String) error.get("message") : "Unknown error";
                                                    connectionLogs.add("连接失败: " + errorMsg);

                                                    // 检查是否为需要配对的错误
                                                    if (errorMsg != null && errorMsg.toLowerCase().contains("pairing")) {
                                                        resultRef.set(GatewayConnectResult.builder()
                                                                .status("pairing_required")
                                                                .message("需要配对设备")
                                                                .errorCode("pairing_required")
                                                                .logs(new ArrayList<>(connectionLogs))
                                                                .build());
                                                    } else {
                                                        resultRef.set(GatewayConnectResult.builder()
                                                                .status("error")
                                                                .message(errorMsg)
                                                                .errorCode("auth_failed")
                                                                .logs(new ArrayList<>(connectionLogs))
                                                                .build());
                                                    }
                                                }
                                                resultLatch.countDown();
                                                webSocketSession.close().subscribe();
                                            }
                                        } catch (Exception e) {
                                            log.error("处理消息失败", e);
                                            connectionLogs.add("处理消息失败: " + e.getMessage());
                                        }
                                    })
                                    .doOnError(err -> {
                                        log.error("WebSocket 接收错误: {}", err.getMessage());
                                        connectionLogs.add("WebSocket 错误: " + err.getMessage());
                                        if (resultRef.get() == null) {
                                            resultRef.set(GatewayConnectResult.builder()
                                                    .status("error")
                                                    .message(err.getMessage())
                                                    .errorCode("ws_error")
                                                    .logs(new ArrayList<>(connectionLogs))
                                                    .build());
                                            resultLatch.countDown();
                                        }
                                    })
                                    .doOnTerminate(() -> {
                                        log.info("WebSocket 连接终止");
                                    })
                                    .then();
                        }
                ).block(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS));
            } catch (Exception e) {
                log.error("WebSocket execute 错误: {}", e.getMessage(), e);
                connectionLogs.add("连接错误: " + e.getMessage());
                resultRef.set(GatewayConnectResult.builder()
                        .status("error")
                        .message(e.getMessage())
                        .errorCode("connection_error")
                        .logs(new ArrayList<>(connectionLogs))
                        .build());
                resultLatch.countDown();
            }

            // 等待结果或超时
            if (!resultLatch.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                connectionLogs.add("连接超时");
                return GatewayConnectResult.builder()
                        .status("error")
                        .message("连接超时")
                        .errorCode("timeout")
                        .logs(connectionLogs)
                        .build();
            }

            GatewayConnectResult result = resultRef.get();
            if (result == null) {
                connectionLogs.add("未收到响应");
                return GatewayConnectResult.builder()
                        .status("error")
                        .message("未收到服务器响应")
                        .errorCode("no_response")
                        .logs(connectionLogs)
                        .build();
            }

            return result;
        }).subscribeOn(Schedulers.boundedElastic())
                .block(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS + 5));
    }

    /**
     * 构建设备认证 Connect 请求参数(用于第一次尝试,无 nonce)
     *
     * 注意: 此方法当前未使用,保留用于参考.
     * 当前流程中,只有在收到挑战后才发送带 nonce 的请求.
     *
     * @param deviceId 设备唯一标识符
     * @param token Gateway 访问令牌
     * @param nonce 挑战 nonce(如果为空则省略该字段)
     * @return JSON 格式的请求字符串
     * @throws Exception 如果构建失败
     */
    private String buildConnectParams(String deviceId, String token, String nonce) throws Exception {
        long signedAtMs = System.currentTimeMillis();

        // 构建设备认证 payload: v3|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce|platform|deviceFamily
        String devicePayload = buildDeviceAuthPayload(
                deviceId, DEFAULT_CLIENT_ID, DEFAULT_CLIENT_MODE,
                "operator", List.of("operator.admin"),
                signedAtMs, token, nonce
        );

        String signature = signPayload(devicePayload);

        // 构建 device 对象,nonce 非空时才包含
        Map<String, Object> deviceMap = new HashMap<>();
        deviceMap.put("id", deviceId);
        deviceMap.put("publicKey", devicePublicKey);
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

        // JSON-RPC 风格请求帧
        String requestId = java.util.UUID.randomUUID().toString();
        Map<String, Object> requestFrame = new HashMap<>();
        requestFrame.put("type", "req");
        requestFrame.put("id", requestId);
        requestFrame.put("method", "connect");
        requestFrame.put("params", connectParams);

        return objectMapper.writeValueAsString(requestFrame);
    }

    /**
     * 构建带 nonce 的 Connect 请求参数(用于响应挑战)
     *
     * 这是实际使用的 connect 请求构建方法.
     * Gateway 要求所有 connect 请求都必须包含从 connect.challenge 获得的 nonce.
     *
     * @param deviceId 设备唯一标识符
     * @param token Gateway 访问令牌
     * @param nonce 从 connect.challenge 事件获得的一次性随机数
     * @return JSON 格式的请求字符串
     * @throws Exception 如果构建失败
     */
    private String buildSecondConnectParams(String deviceId, String token, String nonce) throws Exception {
        long signedAtMs = System.currentTimeMillis();

        // 构建设备认证 payload
        String devicePayload = buildDeviceAuthPayload(
                deviceId, DEFAULT_CLIENT_ID, DEFAULT_CLIENT_MODE,
                "operator", List.of("operator.admin"),
                signedAtMs, token, nonce
        );

        String signature = signPayload(devicePayload);

        // 构建 device 对象,必须包含 nonce
        Map<String, Object> deviceMap = new HashMap<>();
        deviceMap.put("id", deviceId);
        deviceMap.put("publicKey", devicePublicKey);
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

        // JSON-RPC 风格请求帧
        String requestId = java.util.UUID.randomUUID().toString();
        Map<String, Object> requestFrame = new HashMap<>();
        requestFrame.put("type", "req");
        requestFrame.put("id", requestId);
        requestFrame.put("method", "connect");
        requestFrame.put("params", connectParams);

        return objectMapper.writeValueAsString(requestFrame);
    }

    /**
     * 构建设备认证 Payload 字符串
     *
     * Payload 格式(使用 | 分隔):
     * v3|deviceId|clientId|clientMode|role|scopes|signedAtMs|token|nonce|platform|deviceFamily
     *
     * 这个字符串会被 Ed25519 私钥签名后发送给 Gateway,
     * Gateway 用公钥验证签名来确认设备身份.
     *
     * @param deviceId 设备唯一标识符
     * @param clientId 客户端标识符
     * @param clientMode 客户端模式
     * @param role 角色
     * @param scopes 权限范围列表
     * @param signedAtMs 签名时间戳(毫秒)
     * @param token Gateway 访问令牌
     * @param nonce 挑战随机数
     * @return 格式化的 payload 字符串
     */
    private String buildDeviceAuthPayload(String deviceId, String clientId, String clientMode,
                                          String role, List<String> scopes,
                                          long signedAtMs, String token, String nonce) {
        String scopeStr = String.join(",", scopes);
        String platform = DEFAULT_PLATFORM.toLowerCase();
        String deviceFamily = "";
        String payload = String.join("|",
                "v3",
                deviceId != null ? deviceId : "",
                clientId != null ? clientId : "",
                clientMode != null ? clientMode : "",
                role != null ? role : "",
                scopeStr,
                String.valueOf(signedAtMs),
                token != null ? token : "",
                nonce != null ? nonce : "",
                platform,
                deviceFamily
        );
        log.info("Device auth payload v3: {}", payload);
        return payload;
    }
}
