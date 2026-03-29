package com.openclaw.orchestrator.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"skills", "operations"})
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 50)
    private String nickname;

    @Column(length = 100)
    private String role;

    @Column(name = "gateway_url", length = 500)
    private String gatewayUrl;

    @Column(name = "gateway_token", length = 500)
    private String gatewayToken;

    @Column(length = 2000)
    private String personality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WorkerStatus status = WorkerStatus.OFFLINE;

    @Column(length = 500)
    private String avatar;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "worker_skills",
        joinColumns = @JoinColumn(name = "worker_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    @Builder.Default
    @JsonIgnoreProperties("workers")
    private Set<Skill> skills = new HashSet<>();

    @ManyToMany(mappedBy = "workers", fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties("workers")
    private Set<Operation> operations = new HashSet<>();

    @Column(name = "local_runtime")
    @Builder.Default
    private Boolean localRuntime = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "workers"})
    private LLMModel model;

    @Column(name = "system_prompt", length = 4000)
    private String systemPrompt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum WorkerStatus {
        ONLINE, OFFLINE, BUSY
    }
}
