package com.openclaw.orchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private WorkflowInstance instance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private WorkflowStage stage;

    @Column(name = "worker_id")
    private Long workerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approver_comments", columnDefinition = "TEXT")
    private String approverComments;

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

    public enum TaskStatus {
        PENDING,      // 等待分配
        ASSIGNED,     // 已分配 worker，等待执行
        PROCESSING,   // 处理中
        COMPLETED,    // 已完成
        FAILED,       // 失败（可重试）
        PAUSED,       // 暂停
        APPROVED,     // 审批通过
        REJECTED      // 审批拒绝
    }
}
