package com.chad.meaninglog.mq.producer;

import com.chad.meaninglog.config.MqConfig;
import com.chad.meaninglog.entity.AiTaskType;
import com.chad.meaninglog.mq.AiTaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(AiTaskMessage message) {
        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, routingKey(message.taskType()), message);
    }

    private String routingKey(AiTaskType type) {
        return switch (type) {
            case LOG_ANALYZE -> MqConfig.RK_LOG_ANALYZE;
            case LOG_REFINE -> MqConfig.RK_LOG_REFINE;
            case REPORT_GENERATE -> MqConfig.RK_REPORT_GENERATE;
            case REPORT_REFINE -> MqConfig.RK_REPORT_REFINE;
            case CHAT -> MqConfig.RK_CHAT;
        };
    }
}
