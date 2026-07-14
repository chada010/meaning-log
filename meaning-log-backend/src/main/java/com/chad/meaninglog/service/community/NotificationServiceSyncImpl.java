package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.Notification;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.mq.NotificationEnvelope;
import com.chad.meaninglog.mq.producer.NotificationProducer;
import com.chad.meaninglog.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 通知业务实现: 事务内写 DB, afterCommit 通过 RabbitMQ fanout 广播到所有 web 节点, 由本机
 * {@link NotificationSseManager} 推 SSE. 参见 {@link NotificationProducer} 的事务时机说明.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceSyncImpl implements NotificationService {

    private static final int SNIPPET_MAX = 60;

    private final NotificationRepository notificationRepository;
    private final NotificationProducer notificationProducer;
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
            notificationProducer.send(new NotificationEnvelope(n.getReceiverId(), payload));
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
