package com.chad.meaninglog.mq.listener;

import com.chad.meaninglog.config.MqConfig;
import com.chad.meaninglog.mq.AiTaskInputs;
import com.chad.meaninglog.mq.AiTaskMessage;
import com.chad.meaninglog.service.MeaningLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiReportGenerateListener {

    private final AiTaskExecutor executor;
    private final MeaningLogService meaningLogService;

    @RabbitListener(queues = MqConfig.QUEUE_REPORT_GENERATE)
    public void handle(AiTaskMessage message) {
        executor.execute(message, AiTaskInputs.ReportGenerateInput.class, (user, input) ->
                input.mode() == AiTaskInputs.ReportMode.DAILY
                        ? meaningLogService.summarizeDay(user, input.startDate())
                        : meaningLogService.summarizePeriod(user, input.startDate(), input.endDate(), input.title())
        );
    }
}
