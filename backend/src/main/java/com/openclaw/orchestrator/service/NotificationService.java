package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.workflow.NotificationResponse;
import com.openclaw.orchestrator.entity.Notification;
import com.openclaw.orchestrator.entity.Task;
import com.openclaw.orchestrator.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public static final String TYPE_APPROVAL_REQUIRED = "APPROVAL_REQUIRED";
    public static final String TYPE_TASK_COMPLETED = "TASK_COMPLETED";
    public static final String TYPE_WORKFLOW_COMPLETED = "WORKFLOW_COMPLETED";
    public static final String TYPE_WORKFLOW_FAILED = "WORKFLOW_FAILED";

    public void sendApprovalNotification(Task task, Long approverId) {
        String title = "待审批任务";
        String message = String.format("您有一个待审批任务: %s (实例ID: %d)",
                task.getStage().getName(), task.getInstance().getId());

        Notification notification = Notification.builder()
                .type(TYPE_APPROVAL_REQUIRED)
                .title(title)
                .message(message)
                .taskId(task.getId())
                .instanceId(task.getInstance().getId())
                .recipientId(approverId)
                .isRead(false)
                .build();

        notificationRepository.save(notification);

        // TODO: Send email notification if configured
        // emailService.send(approverId, title, message);
    }

    public List<NotificationResponse> getNotificationsForUser(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByRecipientId(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:Notification not found"));

        if (!notification.getRecipientId().equals(userId)) {
            throw new RuntimeException("FORBIDDEN:Notification not for this user");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .taskId(notification.getTaskId())
                .instanceId(notification.getInstanceId())
                .recipientId(notification.getRecipientId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
