package com.chad.meaninglog.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 社区通知 MQ 拓扑: fanout exchange + 每个 web 节点一个 AnonymousQueue.
 *
 * <p>为什么 FanoutExchange 而不是 Topic: SSE emitter 只驻留在本地节点内存里, 消息必须广播到
 * 所有 web 节点, 让持有目标 receiver emitter 的节点自己筛选, 否则 emitter 在 A 节点、消息
 * 被 B 节点独占消费, receiver 永远收不到. 这与 Track 2 AI 任务的"竞争消费"语义完全相反.
 *
 * <p>为什么 AnonymousQueue: 具名共享 queue 会退化成竞争消费. 每节点独立的匿名 queue (exclusive
 * + auto-delete + non-durable) 天然按节点隔离; 节点掉线时 queue 自动清理, 无需运维介入.
 */
@Configuration
public class NotificationMqConfig {

    public static final String NOTIFICATION_FANOUT = "community.notification.fanout";

    @Bean
    public FanoutExchange notificationFanoutExchange() {
        return new FanoutExchange(NOTIFICATION_FANOUT, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, FanoutExchange notificationFanoutExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationFanoutExchange);
    }
}
