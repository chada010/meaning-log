package com.chad.meaninglog.mq.listener;

import com.chad.meaninglog.config.MqConfig;
import com.chad.meaninglog.mq.AiTaskInputs;
import com.chad.meaninglog.mq.AiTaskMessage;
import com.chad.meaninglog.service.XiaojiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiLogRefineListener {

    private final AiTaskExecutor executor;
    private final XiaojiChatService xiaojiChatService;

    @RabbitListener(queues = MqConfig.QUEUE_LOG_REFINE)
    public void handle(AiTaskMessage message) {
        executor.execute(message, AiTaskInputs.LogRefineInput.class,
                (user, input) -> xiaojiChatService.chatWithLog(user, input.logId(), input.message()));
    }
}
