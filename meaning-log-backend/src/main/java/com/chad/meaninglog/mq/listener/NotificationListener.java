package com.chad.meaninglog.mq.listener;

import com.chad.meaninglog.mq.NotificationEnvelope;
import com.chad.meaninglog.service.community.NotificationSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费本节点匿名 queue, 把通知推给本机的 SSE emitter.
 * <p>不落库: 落库在 {@code NotificationServiceSyncImpl} 事务内已完成, MQ 只是"事件传播",
 * 消费者重放/重复消费不应重复插 Notification.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationListener {

    private final NotificationSseManager sseManager;

    @RabbitListener(queues = "#{notificationQueue.name}")
    public void handle(NotificationEnvelope envelope) {
        if (envelope == null || envelope.receiverId() == null) {
            log.warn("收到空 NotificationEnvelope, 丢弃");
            return;
        }
        sseManager.push(envelope.receiverId(), envelope.payload());
    }
}
