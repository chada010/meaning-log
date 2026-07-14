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
public class AiReportRefineListener {

    private final AiTaskExecutor executor;
    private final XiaojiChatService xiaojiChatService;

    @RabbitListener(queues = MqConfig.QUEUE_REPORT_REFINE)
    public void handle(AiTaskMessage message) {
        executor.execute(message, AiTaskInputs.ReportRefineInput.class,
                (user, input) -> xiaojiChatService.chatWithReport(user, input.reportId(), input.message()));
    }
}
