package com.chad.meaninglog.mq;

/**
 * 通知消息封装。receiverId 用于路由到 SSE emitter, payload 是前端可直接消费的 JSON.
 * Listener 拿到后不再落库(落库在 NotificationServiceSyncImpl 事务内已完成), 只做 SSE 推送.
 */
public record NotificationEnvelope(Long receiverId, String payload) {
}
