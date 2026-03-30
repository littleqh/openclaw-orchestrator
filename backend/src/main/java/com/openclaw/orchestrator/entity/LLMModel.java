package com.openclaw.orchestrator.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.openclaw.orchestrator.config.AESEncryptor;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_models")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"workers"})
public class LLMModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(length = 500)
    private String baseUrl;

    @Convert(converter = AESEncryptor.class)
    @Column(length = 500)
    @JsonIgnore
    private String apiKey;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(length = 20)
    @Builder.Default
    private String apiFormat = "openai";

    @OneToMany(mappedBy = "model", fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private java.util.Set<Worker> workers = new java.util.HashSet<>();

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
}
