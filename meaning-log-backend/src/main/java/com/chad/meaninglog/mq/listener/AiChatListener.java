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
public class AiChatListener {

    private final AiTaskExecutor executor;
    private final XiaojiChatService xiaojiChatService;

    @RabbitListener(queues = MqConfig.QUEUE_CHAT)
    public void handle(AiTaskMessage message) {
        executor.execute(message, AiTaskInputs.ChatInput.class,
                (user, input) -> xiaojiChatService.chatWithCompanion(user, input.sessionId(), input.message()));
    }
}
