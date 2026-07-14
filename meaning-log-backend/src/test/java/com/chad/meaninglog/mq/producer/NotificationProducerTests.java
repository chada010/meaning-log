package com.chad.meaninglog.mq.producer;

import com.chad.meaninglog.config.NotificationMqConfig;
import com.chad.meaninglog.mq.NotificationEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 精确验证 NotificationProducer 的事务时机: 事务未提交/回滚 → 不 send;
 * 事务提交 → 才 send. 集成测试(Testcontainers)因 Docker 环境依赖不稳时, 这里是兜底.
 */
class NotificationProducerTests {

    private RabbitTemplate rabbitTemplate;
    private NotificationProducer producer;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        producer = new NotificationProducer(rabbitTemplate);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void sendsImmediatelyWhenNoTransactionIsActive() {
        NotificationEnvelope envelope = new NotificationEnvelope(1L, "payload");

        producer.send(envelope);

        verify(rabbitTemplate).convertAndSend(NotificationMqConfig.NOTIFICATION_FANOUT, "", envelope);
    }

    @Test
    void deferSendUntilAfterCommitWhenTransactionIsActive() {
        NotificationEnvelope envelope = new NotificationEnvelope(1L, "payload");
        TransactionSynchronizationManager.initSynchronization();
        try {
            producer.send(envelope);
            verify(rabbitTemplate, never()).convertAndSend(NotificationMqConfig.NOTIFICATION_FANOUT, "", envelope);

            List<TransactionSynchronization> hooks = TransactionSynchronizationManager.getSynchronizations();
            hooks.forEach(TransactionSynchronization::afterCommit);

            verify(rabbitTemplate, times(1))
                    .convertAndSend(NotificationMqConfig.NOTIFICATION_FANOUT, "", envelope);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void doesNotSendWhenTransactionIsRolledBack() {
        NotificationEnvelope envelope = new NotificationEnvelope(1L, "payload");
        TransactionSynchronizationManager.initSynchronization();
        try {
            producer.send(envelope);

            // 模拟 rollback: 只触发 afterCompletion(ROLLED_BACK), 不触发 afterCommit
            List<TransactionSynchronization> hooks = TransactionSynchronizationManager.getSynchronizations();
            hooks.forEach(h -> h.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(rabbitTemplate, never())
                    .convertAndSend(NotificationMqConfig.NOTIFICATION_FANOUT, "", envelope);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
