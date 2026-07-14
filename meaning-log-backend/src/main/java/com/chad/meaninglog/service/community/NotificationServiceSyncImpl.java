package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.Notification;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 同步实现: 写 DB + Redis Pub/Sub 广播。Track 2 (MQ) 合入后可替换为投递到 community.notification 队列。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceSyncImpl implements NotificationService {

    private static final int SNIPPET_MAX = 60;

    private final NotificationRepository notificationRepository;
    private final CommunityRedisService redis;
    private final ObjectMapper objectMapper;

    @Override
    public void notifyLike(UserAccount actor, Long receiverId, Long publicLogId) {
        if (skipSelfNotify(actor, receiverId)) {
            return;
        }
        Notification n = new Notification();
        n.setReceiverId(receiverId);
        n.setActorId(actor.getId());
        n.setType(Notification.Type.LIKE.name());
        n.setPublicLogId(publicLogId);
        n.setContent(actor.getUsername() + " 赞了你的帖子");
        publish(save(n), actor);
    }

    @Override
    public void notifyComment(UserAccount actor, Long receiverId, Long publicLogId, Long commentId, String snippet) {
        if (skipSelfNotify(actor, receiverId)) {
            return;
        }
        Notification n = new Notification();
        n.setReceiverId(receiverId);
        n.setActorId(actor.getId());
        n.setType(Notification.Type.COMMENT.name());
        n.setPublicLogId(publicLogId);
        n.setCommentId(commentId);
        n.setContent(actor.getUsername() + " 评论: " + trimSnippet(snippet));
        publish(save(n), actor);
    }

    @Override
    public void notifyFollow(UserAccount actor, Long receiverId) {
        if (skipSelfNotify(actor, receiverId)) {
            return;
        }
        Notification n = new Notification();
        n.setReceiverId(receiverId);
        n.setActorId(actor.getId());
        n.setType(Notification.Type.FOLLOW.name());
        n.setContent(actor.getUsername() + " 关注了你");
        publish(save(n), actor);
    }

    @Override
    public Notification save(Notification notification) {
        notificationRepository.insert(notification);
        return notification;
    }

    private void publish(Notification n, UserAccount actor) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "id", n.getId(),
                    "type", n.getType(),
                    "actorId", n.getActorId(),
                    "actorName", actor.getUsername(),
                    "publicLogId", n.getPublicLogId() == null ? "" : n.getPublicLogId(),
                    "commentId", n.getCommentId() == null ? "" : n.getCommentId(),
                    "content", n.getContent() == null ? "" : n.getContent(),
                    "createdAt", n.getCreatedAt() == null ? "" : n.getCreatedAt().toString()
            ));
            redis.publishNotification(n.getReceiverId(), payload);
        } catch (JsonProcessingException e) {
            log.warn("发布通知失败: receiverId={}", n.getReceiverId(), e);
        }
    }

    private boolean skipSelfNotify(UserAccount actor, Long receiverId) {
        return actor == null || receiverId == null || actor.getId().equals(receiverId);
    }

    private String trimSnippet(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > SNIPPET_MAX ? s.substring(0, SNIPPET_MAX) + "..." : s;
    }
}
