package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.Notification;
import com.chad.meaninglog.entity.UserAccount;

/**
 * 通知服务接口。当前采用同步实现 (DB + Redis Pub/Sub), Track 2 (MQ) 合入后可替换为异步投递到 community.notification 队列。
 */
public interface NotificationService {

    void notifyLike(UserAccount actor, Long receiverId, Long publicLogId);

    void notifyComment(UserAccount actor, Long receiverId, Long publicLogId, Long commentId, String snippet);

    void notifyFollow(UserAccount actor, Long receiverId);

    Notification save(Notification notification);
}
