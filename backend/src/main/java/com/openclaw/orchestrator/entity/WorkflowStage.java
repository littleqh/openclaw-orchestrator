package com.openclaw.orchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "workflow_stages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private BusinessWorkflow workflow;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    @Builder.Default
    private TaskType taskType = TaskType.AUTO;

    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "output_schema_id")
    private Long outputSchemaId;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "next_stage_id")
    private Long nextStageId;

    @Column(name = "condition_expr", length = 500)
    private String conditionExpr;

    @Column(name = "x")
    private Integer x;

    @Column(name = "y")
    private Integer y;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<WorkflowStageBranch> branches = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TaskType {
        AUTO,       // 自动环节
        APPROVAL    // 审批环节
    }
}
