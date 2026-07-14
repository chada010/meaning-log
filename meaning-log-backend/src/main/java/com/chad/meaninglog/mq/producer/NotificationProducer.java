package com.chad.meaninglog.mq.producer;

import com.chad.meaninglog.config.NotificationMqConfig;
import com.chad.meaninglog.mq.NotificationEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 通知消息投递. 事务提交后再发消息, 避免 dual-write: 事务内 send 而事务回滚会成为
 * 消费者拉不到 Notification 记录的幽灵通知. 与 Track 2 AiTaskService.create 同款.
 */
@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(NotificationEnvelope envelope) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(NotificationMqConfig.NOTIFICATION_FANOUT, "", envelope);
                }
            });
        } else {
            rabbitTemplate.convertAndSend(NotificationMqConfig.NOTIFICATION_FANOUT, "", envelope);
        }
    }
}
