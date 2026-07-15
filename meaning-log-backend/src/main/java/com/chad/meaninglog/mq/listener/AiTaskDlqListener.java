package com.chad.meaninglog.mq.listener;

import com.chad.meaninglog.config.MqConfig;
import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.mq.AiTaskMessage;
import com.chad.meaninglog.repository.AiTaskRepository;
import com.chad.meaninglog.service.AiTaskNotifier;
import com.chad.meaninglog.service.AiTaskTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiTaskDlqListener {

    private final AiTaskRepository aiTaskRepository;
    private final AiTaskNotifier aiTaskNotifier;

    @RabbitListener(queues = MqConfig.QUEUE_DLQ)
    public void handle(AiTaskMessage message) {
        Long taskId = message.taskId();
        AiTask task = aiTaskRepository.selectById(taskId);
        if (task == null) {
            log.warn("DLQ received unknown AI task {}, dropping", taskId);
            return;
        }
        if (task.getStatus() != AiTaskStatus.PENDING) {
            log.warn("DLQ received AI task {} in status {}, ignoring", taskId, task.getStatus());
            return;
        }
        String errorMessage = task.getErrorMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = "Retries exhausted, moved to DLQ";
        }
        int rows = aiTaskRepository.failPendingFromDlq(taskId, errorMessage, AiTaskTime.now());
        if (rows == 0) {
            log.warn("DLQ AI task {} changed state before failure update, ignoring", taskId);
            return;
        }
        aiTaskNotifier.publishDone(taskId);
        log.warn("AI task {} moved to FAILED after DLQ processing", taskId);
    }
}
