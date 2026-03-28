# 令牌认证与用户管理实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 OpenClaw Orchestrator 实现双轨认证系统：用户 JWT 认证 + Agent UUID 令牌认证

**Architecture:**
- 双轨认证并行：`User <jwt>` 前缀走 JWT 验证，`Agent <uuid>` 前缀走数据库验证
- `AuthFilter` 作为统一入口，根据前缀分流到 `JwtAuthFilter` 或 `AgentTokenFilter`
- `AgentTokenFilter` 记录所有访问日志到 `TokenAccessLog` 表
- 用户注册登录走 `/api/auth/**`，无需认证；其他 API 需要认证

**Tech Stack:**
- Backend: Spring Boot 3.2.5, Spring Data JPA, JJWT (io.jsonwebtoken)
- Frontend: Vue 3, Naive UI, axios, vue-router

---

## 文件结构

### Backend 新增文件

```
backend/src/main/java/com/openclaw/orchestrator/
├── entity/
│   ├── User.java
│   ├── AgentToken.java
│   └── TokenAccessLog.java
├── repository/
│   ├── UserRepository.java
│   ├── AgentTokenRepository.java
│   └── TokenAccessLogRepository.java
├── service/
│   ├── JwtService.java
│   ├── AuthService.java
│   └── AgentTokenService.java
├── filter/
│   ├── AuthFilter.java
│   ├── JwtAuthFilter.java
│   └── AgentTokenFilter.java
├── controller/
│   ├── AuthController.java
│   └── AgentTokenController.java
├── dto/
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   ├── AuthResponse.java
│   ├── TokenCreateRequest.java
│   └── TokenResponse.java
└── config/
    └── WebConfig.java
```

### Backend 修改文件

- `backend/pom.xml` — 添加 JJWT 依赖
- `backend/src/main/resources/application.yml` — 添加 JWT 配置

### Frontend 新增文件

```
frontend/src/
├── views/LoginView.vue
├── views/TokenManagement.vue
├── api/authApi.js
├── api/tokenApi.js
```

### Frontend 修改文件

- `frontend/src/router/index.js` — 添加 LoginView + TokenManagement 路由 + 路由守卫
- `frontend/src/api/index.js` — 添加 axios 拦截器（请求添加 Auth Header，响应 401 处理）

---

## Task 1: 添加 JJWT 依赖

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: 添加 JJWT 依赖到 pom.xml**

在 `<dependencies>` 中添加：

```xml
<!-- JJWT for JWT handling -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
```

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS（无输出）

- [ ] **Step 2: Commit**

```bash
git add backend/pom.xml && git commit -m "deps: add jjwt for JWT handling"
```

---

## Task 2: 添加 JWT 配置

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 末尾添加 JWT 配置**

```yaml
jwt:
  secret: openclaw-orchestrator-jwt-secret-key-change-in-production-2024
  expiration: 86400000  # 24 hours in milliseconds
```

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/application.yml && git commit -m "config: add jwt secret and expiration"
```

---

## Task 3: User 实体 + Repository

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/entity/User.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/repository/UserRepository.java`

- [ ] **Step 1: 创建 User 实体**

```java
package com.openclaw.orchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "ADMIN";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: 创建 UserRepository**

```java
package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

- [ ] **Step 3: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/entity/User.java backend/src/main/java/com/openclaw/orchestrator/repository/UserRepository.java && git commit -m "feat: add User entity and repository"
```

---

## Task 4: AgentToken 实体 + Repository + TokenAccessLog 实体 + Repository

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/entity/AgentToken.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/repository/AgentTokenRepository.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/entity/TokenAccessLog.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/repository/TokenAccessLogRepository.java`

- [ ] **Step 1: 创建 AgentToken 实体**

```java
package com.openclaw.orchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastAccessAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: 创建 AgentTokenRepository**

```java
package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.AgentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AgentTokenRepository extends JpaRepository<AgentToken, Long> {
    Optional<AgentToken> findByToken(String token);

    Optional<AgentToken> findByWorkerId(Long workerId);

    @Query("SELECT at FROM AgentToken at LEFT JOIN FETCH at.worker WHERE at.worker IS NULL")
    Optional<AgentToken> findSystemToken();

    @Query("SELECT at FROM AgentToken at LEFT JOIN FETCH at.worker WHERE at.worker IS NOT NULL")
    List<AgentToken> findAllAgentTokens();

    boolean existsByWorkerId(Long workerId);
}
```

- [ ] **Step 3: 创建 TokenAccessLog 实体**

```java
package com.openclaw.orchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "token_access_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id", nullable = false)
    private AgentToken agentToken;

    @Column(name = "access_path", nullable = false, length = 255)
    private String accessPath;

    @Column(name = "access_ip", nullable = false, length = 45)
    private String accessIp;

    @Column(name = "access_time", nullable = false)
    private LocalDateTime accessTime;

    @Column(nullable = false)
    private Boolean success;

    @PrePersist
    protected void onCreate() {
        accessTime = LocalDateTime.now();
    }
}
```

- [ ] **Step 4: 创建 TokenAccessLogRepository**

```java
package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.TokenAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TokenAccessLogRepository extends JpaRepository<TokenAccessLog, Long> {
    List<TokenAccessLog> findByAgentTokenIdOrderByAccessTimeDesc(Long tokenId);
}
```

- [ ] **Step 5: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/entity/AgentToken.java backend/src/main/java/com/openclaw/orchestrator/entity/TokenAccessLog.java backend/src/main/java/com/openclaw/orchestrator/repository/AgentTokenRepository.java backend/src/main/java/com/openclaw/orchestrator/repository/TokenAccessLogRepository.java && git commit -m "feat: add AgentToken and TokenAccessLog entities and repositories"
```

---

## Task 5: DTO 类

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/dto/AuthResponse.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/dto/TokenCreateRequest.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/dto/TokenResponse.java`

- [ ] **Step 1: 创建 LoginRequest**

```java
package com.openclaw.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String password;
}
```

- [ ] **Step 2: 创建 RegisterRequest**

```java
package com.openclaw.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String password;
}
```

- [ ] **Step 3: 创建 AuthResponse**

```java
package com.openclaw.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private Long id;
    private String username;
    private String role;
    private String token;
}
```

- [ ] **Step 4: 创建 TokenCreateRequest**

```java
package com.openclaw.orchestrator.dto;

import lombok.Data;

@Data
public class TokenCreateRequest {
    private Long workerId; // null for system token
}
```

- [ ] **Step 5: 创建 TokenResponse**

```java
package com.openclaw.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    private Long id;
    private String token;        // Full token, only on create/reset
    private String tokenPreview; // Masked token, e.g. "****-****-****-a716"
    private String type;        // "SYSTEM" or "AGENT"
    private Long workerId;
    private String workerName;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessAt;
}
```

- [ ] **Step 6: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/dto/*.java && git commit -m "feat: add auth and token DTOs"
```

---

## Task 6: JwtService

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/service/JwtService.java`

- [ ] **Step 1: 创建 JwtService**

```java
package com.openclaw.orchestrator.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        Claims claims = getClaims(token);
        return ((Number) claims.get("userId")).longValue();
    }

    public String getUsername(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/service/JwtService.java && git commit -m "feat: add JwtService for JWT generation and validation"
```

---

## Task 7: AuthService

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/service/AuthService.java`

- [ ] **Step 1: 创建 AuthService**

```java
package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.AuthResponse;
import com.openclaw.orchestrator.dto.LoginRequest;
import com.openclaw.orchestrator.dto.RegisterRequest;
import com.openclaw.orchestrator.entity.User;
import com.openclaw.orchestrator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("CONFLICT:Username already exists");
        }
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ADMIN")
                .build();
        return userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("401:Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("401:Invalid credentials");
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .token(token)
                .build();
    }
}
```

**Note:** 需要在 pom.xml 中添加 Spring Security 依赖（PasswordEncoder）。如果不想引入完整 Spring Security，可以用一个简单的 BCryptPasswordEncoder bean 或者手动实现一个简单的密码验证器。但 Spring Boot Starters 通常包含 Spring Security 的密码编码器。实际项目中建议添加 spring-boot-starter-security，但这里为了最小化改动，我们可以直接用一个简单的 PasswordEncoder 实现。

实际上，JJWT 也不包含 PasswordEncoder，所以需要添加依赖。让我先更新 AuthService 使用简单的密码编码。

实际上，让我简化一下——使用一个简单的内部 PasswordEncoder 实现来避免额外依赖：

```java
// 临时使用简单实现
// 实际项目建议使用 BCryptPasswordEncoder from spring-security
```

我需要重新考虑。实际上最简单的方式是添加 spring-boot-starter-security 依赖，这样就有 PasswordEncoder 了。但为了避免复杂化，我们可以用一个简单的 MD5 或 SHA 简单实现先跑通，或者直接添加依赖。

让我修改 Task 1 的 pom.xml 添加 spring-security：

- [ ] **Step 1: 更新 pom.xml 添加 Spring Security 依赖**

```xml
<!-- Spring Security for PasswordEncoder -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

- [ ] **Step 2: 重写 AuthService**

```java
package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.AuthResponse;
import com.openclaw.orchestrator.dto.LoginRequest;
import com.openclaw.orchestrator.dto.RegisterRequest;
import com.openclaw.orchestrator.entity.User;
import com.openclaw.orchestrator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("CONFLICT:Username already exists");
        }
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ADMIN")
                .build();
        return userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("401:Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("401:Invalid credentials");
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .token(token)
                .build();
    }
}
```

- [ ] **Step 3: 创建 SecurityConfig 禁用 CSRF 并配置密码编码器**

Create: `backend/src/main/java/com/openclaw/orchestrator/config/SecurityConfig.java`

```java
package com.openclaw.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().permitAll() // Filter will handle auth
            );
        return http.build();
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/openclaw/orchestrator/service/AuthService.java backend/src/main/java/com/openclaw/orchestrator/config/SecurityConfig.java && git commit -m "feat: add AuthService with Spring Security and BCrypt"
```

---

## Task 8: AuthController

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/controller/AuthController.java`

- [ ] **Step 1: 创建 AuthController**

```java
package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.AuthResponse;
import com.openclaw.orchestrator.dto.LoginRequest;
import com.openclaw.orchestrator.dto.RegisterRequest;
import com.openclaw.orchestrator.entity.User;
import com.openclaw.orchestrator.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/controller/AuthController.java && git commit -m "feat: add AuthController for register and login"
```

---

## Task 9: AgentTokenService

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/service/AgentTokenService.java`

- [ ] **Step 1: 创建 AgentTokenService**

```java
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
```

- [ ] **Step 2: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/service/AgentTokenService.java && git commit -m "feat: add AgentTokenService for token management"
```

---

## Task 10: AgentTokenController

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/controller/AgentTokenController.java`

- [ ] **Step 1: 创建 AgentTokenController**

```java
package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.TokenCreateRequest;
import com.openclaw.orchestrator.dto.TokenResponse;
import com.openclaw.orchestrator.service.AgentTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
```

- [ ] **Step 2: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/controller/AgentTokenController.java && git commit -m "feat: add AgentTokenController for token CRUD"
```

---

## Task 11: AuthFilter + JwtAuthFilter + AgentTokenFilter

**Files:**
- Create: `backend/src/main/java/com/openclaw/orchestrator/filter/AuthFilter.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/filter/JwtAuthFilter.java`
- Create: `backend/src/main/java/com/openclaw/orchestrator/filter/AgentTokenFilter.java`

- [ ] **Step 1: 创建 AuthFilter**

```java
package com.openclaw.orchestrator.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Allow auth endpoints without authentication
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        // For /api/** paths, authentication is handled by JwtAuthFilter and AgentTokenFilter
        // This filter mainly ensures auth endpoints are accessible
        if (!path.startsWith("/api/")) {
            // Non-API paths (e.g., static resources) pass through
            chain.doFilter(request, response);
            return;
        }

        // Let the more specific filters handle the actual authentication
        // AuthFilter just acts as a traffic director here
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: 创建 JwtAuthFilter**

```java
package com.openclaw.orchestrator.filter;

import com.openclaw.orchestrator.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("User ")) {
            String token = authHeader.substring(5);
            if (jwtService.validateToken(token)) {
                Claims claims = jwtService.getClaims(token);
                String username = claims.getSubject();
                String role = (String) claims.get("role");

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: 创建 AgentTokenFilter**

```java
package com.openclaw.orchestrator.filter;

import com.openclaw.orchestrator.entity.AgentToken;
import com.openclaw.orchestrator.service.AgentTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class AgentTokenFilter extends OncePerRequestFilter {

    private final AgentTokenService agentTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Agent ")) {
            String token = authHeader.substring(6);
            AgentToken agentToken = agentTokenService.validateToken(token);

            String clientIp = getClientIp(request);
            String path = request.getRequestURI();

            if (agentToken != null) {
                agentTokenService.logAccess(agentToken, path, clientIp, true);

                // Set authentication for Agent
                String principal = "AGENT:" + (agentToken.getWorker() != null ? agentToken.getWorker().getId() : "SYSTEM");
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_AGENT"))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Log failed access - we need a token object for this
                // For failed auth, we create a temporary log entry
                // This is a limitation - we don't know which token was attempted
                // In production, you might want to pass the attempted token to the log
                if (token != null) {
                    AgentToken dummyToken = new AgentToken();
                    dummyToken.setId(-1L);
                    agentTokenService.logAccess(dummyToken, path, clientIp, false);
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Invalid token\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

- [ ] **Step 4: 更新 SecurityConfig 移除 permitAll 并注册 Filters**

```java
package com.openclaw.orchestrator.config;

import com.openclaw.orchestrator.filter.AgentTokenFilter;
import com.openclaw.orchestrator.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AgentTokenFilter agentTokenFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(agentTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 5: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/filter/*.java backend/src/main/java/com/openclaw/orchestrator/config/SecurityConfig.java && git commit -m "feat: add AuthFilter, JwtAuthFilter, AgentTokenFilter with dual auth"
```

---

## Task 12: 修复 GlobalExceptionHandler 支持 401

**Files:**
- Modify: `backend/src/main/java/com/openclaw/orchestrator/controller/GlobalExceptionHandler.java`

- [ ] **Step 1: 更新 GlobalExceptionHandler 处理 401 前缀**

```java
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
    Map<String, String> error = new HashMap<>();
    String message = e.getMessage();
    if (message != null && message.startsWith("NOT_FOUND:")) {
        error.put("message", message.substring(10));
        return ResponseEntity.status(404).body(error);
    }
    if (message != null && message.startsWith("CONFLICT:")) {
        error.put("message", message.substring(9));
        return ResponseEntity.status(409).body(error);
    }
    if (message != null && message.startsWith("401:")) {
        error.put("message", message.substring(4));
        return ResponseEntity.status(401).body(error);
    }
    error.put("message", message != null ? message : "Unknown error");
    return ResponseEntity.badRequest().body(error);
}
```

- [ ] **Step 2: 验证编译**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/openclaw/orchestrator/controller/GlobalExceptionHandler.java && git commit -m "fix: add 401 prefix handling to GlobalExceptionHandler"
```

---

## Task 13: 前端 - authApi.js + tokenApi.js

**Files:**
- Create: `frontend/src/api/authApi.js`
- Create: `frontend/src/api/tokenApi.js`

- [ ] **Step 1: 创建 authApi.js**

```javascript
import { post } from './index'

export const authApi = {
  login: (username, password) => post('/api/auth/login', { username, password }),
  register: (username, password) => post('/api/auth/register', { username, password }),
}
```

- [ ] **Step 2: 创建 tokenApi.js**

```javascript
import { get, post, put, delete } from './index'

export const tokenApi = {
  getAll: () => get('/api/tokens'),
  create: (workerId) => post('/api/tokens', workerId != null ? { workerId } : {}),
  getById: (id) => get(`/api/tokens/${id}`),
  reset: (id) => put(`/api/tokens/${id}/reset`),
  delete: (id) => delete(`/api/tokens/${id}`),
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/authApi.js frontend/src/api/tokenApi.js && git commit -m "feat: add authApi and tokenApi"
```

---

## Task 14: 前端 - axios 拦截器

**Files:**
- Modify: `frontend/src/api/index.js`

- [ ] **Step 1: 更新 index.js 添加拦截器**

在文件开头添加：

```javascript
import axios from 'axios'
import router from '../router'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// Request interceptor: add Authorization header
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `User ${token}`
  }
  return config
})

// Response interceptor: handle 401
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)
```

确保导出 api 实例用于其他模块使用。查看现有代码，确认 `post`, `get` 等函数定义位置，可能需要调整为使用 `api` 实例的方法。

实际上，看现有代码，`post` 和 `get` 是从 `./index` 导入的，但当前 index.js 导出的是 `instanceApi`, `skillApi` 等，没有导出 `post` 和 `get`。我需要修改 index.js 来正确导出这些方法，或者直接使用 api 实例。

最简单的修改是让 authApi.js 和 tokenApi.js 直接使用 axios 实例而不是依赖导出函数：

- [ ] **Step 1: 重写 authApi.js 直接使用 axios**

```javascript
import axios from 'axios'

const authApi = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export const login = (username, password) =>
  authApi.post('/api/auth/login', { username, password }).then(r => r.data)

export const register = (username, password) =>
  authApi.post('/api/auth/register', { username, password }).then(r => r.data)
```

实际上，让我看一下现有的 api/index.js 是怎么导出函数的。问题在于现有代码导出的是 `instanceApi` 等，但 authApi 和 tokenApi 需要直接使用 axios。最好的方式是：

1. 修改 index.js 添加拦截器并导出 axios 实例
2. 让 authApi.js 和 tokenApi.js 从 index.js 导入 axios 实例

但实际上，现有 index.js 是按需导出的不同 API 对象，而添加拦截器需要在 axios 实例上添加。所以最简单的方式是：

1. 修改 index.js 让它先创建 axios 实例（带拦截器），然后导出这个实例
2. 其他 API 文件从 index.js 导入 axios 实例

让我检查现有 index.js 的完整内容来确认导入导出方式：

```javascript
import axios from 'axios'

const api = axios.create({...})
// 现有代码没有拦截器

export const instanceApi = {...}
export const skillApi = {...}
export const operationApi = {...}
export function connectSse(...) {...}
```

关键是 `api` 实例没有导出。我需要修改为：

1. 在 index.js 中添加拦截器
2. 导出 `api` 实例
3. 修改 authApi.js 和 tokenApi.js 从 index.js 导入 `api`

- [ ] **Step 1: 修改 index.js**

```javascript
import axios from 'axios'
import router from '../router'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// Request interceptor: add Authorization header
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `User ${token}`
  }
  return config
})

// Response interceptor: handle 401
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

// Re-export helper methods for other api modules
export const post = (url, data) => api.post(url, data).then(r => r.data)
export const get = (url, params) => api.get(url, { params }).then(r => r.data)
export const put = (url, data) => api.put(url, data).then(r => r.data)
export const delete = (url) => api.delete(url)

// Instance management
export const instanceApi = {
  list: () => api.get('/instances').then(r => r.data),
  create: (data) => api.post('/instances', data).then(r => r.data),
  delete: (id) => api.delete(`/instances/${id}`),
  getStatus: (id) => api.get(`/instances/${id}/status`).then(r => r.data),
  getSessions: (id) => api.get(`/instances/${id}/sessions`).then(r => r.data),
  getSubagents: (id) => api.get(`/instances/${id}/subagents`).then(r => r.data),
  searchMemory: (id, query, maxResults) =>
    api.get(`/instances/${id}/memory`, { params: { query, maxResults } }).then(r => r.data),
  invoke: (id, tool, args) =>
    api.post(`/instances/${id}/invoke`, { tool, args }).then(r => r.data)
}

// Skill management
export const skillApi = {
  list: () => api.get('/skills').then(r => r.data),
  get: (id) => api.get(`/skills/${id}`).then(r => r.data),
  create: (data) => api.post('/skills', data).then(r => r.data),
  update: (id, data) => api.put(`/skills/${id}`, data).then(r => r.data),
  delete: (id) => api.delete(`/skills/${id}`),
  checkDelete: (id) => api.get(`/skills/${id}/check-delete`).then(r => r.data),
  forceDelete: (id) => api.delete(`/skills/${id}/force`)
}

// Operation management
export const operationApi = {
  list: () => api.get('/operations').then(r => r.data),
  get: (id) => api.get(`/operations/${id}`).then(r => r.data),
  create: (data) => api.post('/operations', data).then(r => r.data),
  update: (id, data) => api.put(`/operations/${id}`, data).then(r => r.data),
  delete: (id) => api.delete(`/operations/${id}`)
}

// SSE connection
export function connectSse(instanceId, onMessage, onError) {
  const url = `/api/sse/status/${instanceId}`
  const eventSource = new EventSource(url)

  eventSource.addEventListener('status', (event) => {
    try {
      const data = JSON.parse(event.data)
      onMessage(data)
    } catch (e) {
      console.error('SSE parse error:', e)
    }
  })

  eventSource.onerror = (e) => {
    console.error('SSE error:', e)
    onError && onError(e)
  }

  return eventSource
}

export default api
```

- [ ] **Step 2: 重写 authApi.js**

```javascript
import { post } from './index'

export const authApi = {
  login: (username, password) => post('/api/auth/login', { username, password }),
  register: (username, password) => post('/api/auth/register', { username, password }),
}
```

- [ ] **Step 3: 重写 tokenApi.js**

```javascript
import { get, post, put, delete } from './index'

export const tokenApi = {
  getAll: () => get('/api/tokens'),
  create: (workerId) => post('/api/tokens', workerId != null ? { workerId } : {}),
  getById: (id) => get(`/api/tokens/${id}`),
  reset: (id) => put(`/api/tokens/${id}/reset`),
  delete: (id) => delete(`/api/tokens/${id}`),
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/index.js frontend/src/api/authApi.js frontend/src/api/tokenApi.js && git commit -m "feat: add axios interceptors for auth and re-export helpers"
```

---

## Task 15: 前端 - LoginView

**Files:**
- Create: `frontend/src/views/LoginView.vue`

- [ ] **Step 1: 创建 LoginView.vue**

```vue
<template>
  <div class="login-container">
    <n-card class="login-card" title="OpenClaw Orchestrator">
      <n-tabs v-model:value="activeTab" type="line" animated>
        <n-tab-pane name="login" tab="登录">
          <n-form :model="loginForm" @submit.prevent="handleLogin">
            <n-form-item path="username" label="用户名">
              <n-input v-model:value="loginForm.username" placeholder="请输入用户名" />
            </n-form-item>
            <n-form-item path="password" label="密码">
              <n-input v-model:value="loginForm.password" type="password" placeholder="请输入密码" show-password-on="mousedown" />
            </n-form-item>
            <n-button type="primary" block :loading="loading" @click="handleLogin">
              登录
            </n-button>
          </n-form>
        </n-tab-pane>

        <n-tab-pane name="register" tab="注册">
          <n-form :model="registerForm" @submit.prevent="handleRegister">
            <n-form-item path="username" label="用户名">
              <n-input v-model:value="registerForm.username" placeholder="请输入用户名" />
            </n-form-item>
            <n-form-item path="password" label="密码">
              <n-input v-model:value="registerForm.password" type="password" placeholder="请输入密码" show-password-on="mousedown" />
            </n-form-item>
            <n-form-item path="confirmPassword" label="确认密码">
              <n-input v-model:value="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" show-password-on="mousedown" />
            </n-form-item>
            <n-button type="primary" block :loading="loading" @click="handleRegister">
              注册
            </n-button>
          </n-form>
        </n-tab-pane>
      </n-tabs>

      <n-alert v-if="errorMessage" type="error" class="mt-4">
        {{ errorMessage }}
      </n-alert>
    </n-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { NCard, NTabs, NTabPane, NForm, NFormItem, NInput, NButton, NAlert, useMessage } from 'naive-ui'
import { authApi } from '../api/authApi'

const router = useRouter()
const message = useMessage()
const activeTab = ref('login')
const loading = ref(false)
const errorMessage = ref('')

const loginForm = ref({ username: '', password: '' })
const registerForm = ref({ username: '', password: '', confirmPassword: '' })

const handleLogin = async () => {
  errorMessage.value = ''
  if (!loginForm.value.username || !loginForm.value.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const response = await authApi.login(loginForm.value.username, loginForm.value.password)
    localStorage.setItem('token', response.token)
    localStorage.setItem('username', response.username)
    message.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '登录失败'
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  errorMessage.value = ''
  if (!registerForm.value.username || !registerForm.value.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }
  if (registerForm.value.password.length < 6) {
    errorMessage.value = '密码至少6位'
    return
  }
  loading.value = true
  try {
    await authApi.register(registerForm.value.username, registerForm.value.password)
    message.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.value.username = registerForm.value.username
    loginForm.value.password = ''
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
  max-width: 90vw;
}
.mt-4 {
  margin-top: 16px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/LoginView.vue && git commit -m "feat: add LoginView with tabs for login and register"
```

---

## Task 16: 前端 - TokenManagement 页面

**Files:**
- Create: `frontend/src/views/TokenManagement.vue`

- [ ] **Step 1: 创建 TokenManagement.vue**

```vue
<template>
  <div class="token-management">
    <n-tabs type="line" animated>
      <n-tab-pane name="system" tab="系统令牌">
        <div class="token-section">
          <n-card v-if="systemToken" title="系统令牌">
            <n-descriptions :column="2">
              <n-descriptions-item label="令牌预览">{{ systemToken.tokenPreview }}</n-descriptions-item>
              <n-descriptions-item label="创建时间">{{ formatDate(systemToken.createdAt) }}</n-descriptions-item>
              <n-descriptions-item label="最后访问">{{ formatDate(systemToken.lastAccessAt) }}</n-descriptions-item>
            </n-descriptions>
            <template #action>
              <n-space>
                <n-button size="small" @click="copyToken(systemToken)">复制令牌</n-button>
                <n-button size="small" type="warning" @click="handleReset(systemToken)">重置令牌</n-button>
              </n-space>
            </template>
          </n-card>
          <n-card v-else title="系统令牌">
            <n-empty description="暂无系统令牌">
              <template #extra>
                <n-button type="primary" @click="createSystemToken">创建系统令牌</n-button>
              </template>
            </n-empty>
          </n-card>
        </div>
      </n-tab-pane>

      <n-tab-pane name="agent" tab="Agent 令牌">
        <div class="token-section">
          <n-card title="Agent 令牌列表">
            <template #action>
              <n-button type="primary" size="small" @click="showCreateModal = true">创建令牌</n-button>
            </template>
            <n-table :columns="columns" :data="agentTokens" :pagination="false" striped>
              <template #tokenPreview="{ row }">
                {{ row.tokenPreview || '—' }}
              </template>
              <template #actions="{ row }">
                <n-space>
                  <n-button size="tiny" @click="copyToken(row)">复制</n-button>
                  <n-button size="tiny" type="warning" @click="handleReset(row)">重置</n-button>
                  <n-button size="tiny" type="error" @click="handleDelete(row)">删除</n-button>
                </n-space>
              </template>
            </n-table>
            <n-empty v-if="agentTokens.length === 0" description="暂无 Agent 令牌" />
          </n-card>
        </div>
      </n-tab-pane>
    </n-tabs>

    <!-- Create Token Modal -->
    <n-modal v-model:show="showCreateModal" preset="card" title="创建 Agent 令牌" style="width: 400px">
      <n-form>
        <n-form-item label="选择 Worker">
          <n-select v-model:value="selectedWorkerId" :options="workerOptions" placeholder="请选择 Worker" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space>
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" @click="handleCreateAgentToken" :loading="loading">创建</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import { NCard, NTabs, NTabPane, NButton, NSpace, NTable, NEmpty, NModal, NForm, NFormItem, NSelect, NDescriptions, NDescriptionsItem } from 'naive-ui'
import { tokenApi } from '../api/tokenApi'
import { workerApi } from '../api/workerApi'

const message = useMessage()
const dialog = useDialog()

const tokens = ref([])
const workers = ref([])
const showCreateModal = ref(false)
const selectedWorkerId = ref(null)
const loading = ref(false)

const systemToken = computed(() => tokens.value.find(t => t.type === 'SYSTEM'))
const agentTokens = computed(() => tokens.value.filter(t => t.type === 'AGENT'))

const workerOptions = computed(() =>
  workers.value
    .filter(w => !tokens.value.some(t => t.workerId === w.id))
    .map(w => ({ label: w.name, value: w.id }))
)

const columns = [
  { title: 'Worker', key: 'workerName' },
  { title: '令牌预览', key: 'tokenPreview' },
  { title: '创建时间', key: 'createdAt', render: (row) => formatDate(row.createdAt) },
  { title: '操作', key: 'actions', slot: 'actions' }
]

const loadData = async () => {
  try {
    const [tokenData, workerData] = await Promise.all([
      tokenApi.getAll(),
      workerApi.list()
    ])
    tokens.value = tokenData
    workers.value = workerData
  } catch (error) {
    message.error('加载数据失败')
  }
}

const createSystemToken = async () => {
  loading.value = true
  try {
    await tokenApi.create(null)
    message.success('系统令牌创建成功')
    await loadData()
  } catch (error) {
    message.error(error.response?.data?.message || '创建失败')
  } finally {
    loading.value = false
  }
}

const handleCreateAgentToken = async () => {
  if (!selectedWorkerId.value) {
    message.warning('请选择 Worker')
    return
  }
  loading.value = true
  try {
    await tokenApi.create(selectedWorkerId.value)
    message.success('Agent 令牌创建成功')
    showCreateModal.value = false
    selectedWorkerId.value = null
    await loadData()
  } catch (error) {
    message.error(error.response?.data?.message || '创建失败')
  } finally {
    loading.value = false
  }
}

const handleReset = async (token) => {
  dialog.warning({
    title: '确认重置',
    content: '重置后旧令牌将失效，确定要重置吗？',
    positiveText: '确定',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await tokenApi.reset(token.id)
        message.success('令牌已重置')
        await loadData()
      } catch (error) {
        message.error(error.response?.data?.message || '重置失败')
      }
    }
  })
}

const handleDelete = async (token) => {
  dialog.warning({
    title: '确认删除',
    content: '删除后无法恢复，确定要删除吗？',
    positiveText: '确定',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await tokenApi.delete(token.id)
        message.success('令牌已删除')
        await loadData()
      } catch (error) {
        message.error(error.response?.data?.message || '删除失败')
      }
    }
  })
}

const copyToken = async (token) => {
  // Note: token.token is the full token, only available on create/reset response
  // For existing tokens, we need to fetch the full token
  try {
    const fullToken = await tokenApi.getById(token.id)
    if (fullToken.token) {
      await navigator.clipboard.writeText(fullToken.token)
      message.success('令牌已复制到剪贴板')
    } else {
      message.warning('无法复制完整令牌，请重置后复制')
    }
  } catch (error) {
    message.error('复制失败')
  }
}

const formatDate = (date) => {
  if (!date) return '—'
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(loadData)
</script>

<style scoped>
.token-management {
  padding: 24px;
}
.token-section {
  margin-top: 16px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/TokenManagement.vue && git commit -m "feat: add TokenManagement view with system and agent token tabs"
```

---

## Task 17: 前端 - 路由守卫

**Files:**
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 更新 router/index.js 添加 LoginView 和 TokenManagement 路由 + 路由守卫**

```javascript
import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import InstanceManager from '../views/InstanceManager.vue'
import MonitorView from '../views/MonitorView.vue'
import WorkerView from '../views/WorkerView.vue'
import TaskFlowView from '../views/TaskFlowView.vue'
import SkillView from '../views/SkillView.vue'
import OperationView from '../views/OperationView.vue'
import LoginView from '../views/LoginView.vue'
import TokenManagement from '../views/TokenManagement.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard },
  { path: '/instances', name: 'Instances', component: InstanceManager },
  { path: '/monitor', name: 'Monitor', component: MonitorView },
  { path: '/workers', name: 'Workers', component: WorkerView },
  { path: '/task-flow', name: 'TaskFlow', component: TaskFlowView },
  { path: '/skills', name: 'Skills', component: SkillView },
  { path: '/operations', name: 'Operations', component: OperationView },
  { path: '/login', name: 'Login', component: LoginView },
  { path: '/token-management', name: 'TokenManagement', component: TokenManagement, meta: { requiresAuth: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard for auth
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/router/index.js && git commit -m "feat: add router guards for auth and new routes"
```

---

## Task 18: 验证

**Backend:**
- Run: `cd backend && mvn spring-boot:run`
- 测试注册: `curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"`
- 测试登录: `curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"`
- 测试创建系统令牌 (需要带 User token): `curl -X POST http://localhost:8080/api/tokens -H "Authorization: User <token>"`
- 测试 Agent 认证: `curl http://localhost:8080/api/workers -H "Authorization: Agent <uuid>"`

**Frontend:**
- Run: `cd frontend && npm run dev`
- 访问 http://localhost:5173/login
- 注册/登录
- 访问 http://localhost:5173/token-management
- 创建系统令牌和 Agent 令牌
