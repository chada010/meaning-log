package com.chad.meaninglog.dto.community;

import com.chad.meaninglog.entity.Notification;
import com.chad.meaninglog.entity.UserAccount;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {

    private Long id;
    private String type;
    private AuthorInfo actor;
    private Long publicLogId;
    private Long commentId;
    private String content;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification, UserAccount actor) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .actor(AuthorInfo.from(actor))
                .publicLogId(notification.getPublicLogId())
                .commentId(notification.getCommentId())
                .content(notification.getContent())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
