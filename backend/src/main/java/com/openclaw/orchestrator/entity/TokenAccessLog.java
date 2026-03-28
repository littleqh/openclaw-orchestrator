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