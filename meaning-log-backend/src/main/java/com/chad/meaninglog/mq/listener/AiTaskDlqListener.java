package com.chad.meaninglog.mq.listener;

import com.chad.meaninglog.config.MqConfig;
import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.mq.AiTaskMessage;
import com.chad.meaninglog.repository.AiTaskRepository;
import com.chad.meaninglog.service.AiTaskNotifier;
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
        if (task.getStatus() == AiTaskStatus.SUCCESS) {
            log.warn("DLQ received AI task {} that is already SUCCESS, ignoring", taskId);
            return;
        }
        task.setStatus(AiTaskStatus.FAILED);
        if (task.getErrorMessage() == null || task.getErrorMessage().isBlank()) {
            task.setErrorMessage("Retries exhausted, moved to DLQ");
        }
        aiTaskRepository.updateById(task);
        aiTaskNotifier.publishDone(taskId);
        log.warn("AI task {} moved to FAILED after DLQ processing", taskId);
    }
}
