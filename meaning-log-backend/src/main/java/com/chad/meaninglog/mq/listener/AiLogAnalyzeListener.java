package com.chad.meaninglog.mq.listener;

import com.chad.meaninglog.config.MqConfig;
import com.chad.meaninglog.mq.AiTaskInputs;
import com.chad.meaninglog.mq.AiTaskMessage;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.service.MeaningLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiLogAnalyzeListener {

    private final AiTaskExecutor executor;
    private final MeaningLogService meaningLogService;
    private final AiService aiService;

    @RabbitListener(queues = MqConfig.QUEUE_LOG_ANALYZE)
    public void handle(AiTaskMessage message) {
        executor.execute(message, AiTaskInputs.LogAnalyzeInput.class, (user, input) -> {
            MeaningLogService.AnalyzeStreamContext ctx =
                    meaningLogService.prepareAnalyzeStream(user, input.logId());
            return aiService.analyzeLog(ctx.log(), ctx.images());
        });
    }
}
