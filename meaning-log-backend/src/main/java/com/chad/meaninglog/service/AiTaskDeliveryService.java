package com.chad.meaninglog.service;

import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.mq.AiTaskMessage;
import com.chad.meaninglog.mq.producer.AiTaskProducer;
import com.chad.meaninglog.repository.AiTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskDeliveryService {

    private final AiTaskRepository aiTaskRepository;
    private final AiTaskProducer aiTaskProducer;
    private final Clock businessClock;

    public void publishImmediately(AiTaskMessage message) {
        LocalDateTime attemptedAt = LocalDateTime.now(businessClock);
        if (reserveImmediatePublish(message.taskId(), attemptedAt)) {
            send(message);
        }
    }

    public boolean republishIfDue(AiTask task, LocalDateTime cutoff) {
        LocalDateTime attemptedAt = LocalDateTime.now(businessClock);
        if (!reserveRecoveryPublish(task.getId(), cutoff, attemptedAt)) {
            return false;
        }
        send(new AiTaskMessage(task.getId(), task.getTaskType()));
        return true;
    }

    private boolean reserveImmediatePublish(Long taskId, LocalDateTime attemptedAt) {
        try {
            return aiTaskRepository.recordPublishAttempt(taskId, attemptedAt) > 0;
        } catch (RuntimeException ex) {
            log.warn("Failed to record immediate AI task publish attempt: taskId={}", taskId, ex);
            return true;
        }
    }

    private boolean reserveRecoveryPublish(Long taskId, LocalDateTime cutoff, LocalDateTime attemptedAt) {
        try {
            return aiTaskRepository.recordDuePublishAttempt(taskId, cutoff, attemptedAt) > 0;
        } catch (RuntimeException ex) {
            log.warn("Failed to reserve recovered AI task publish attempt: taskId={}", taskId, ex);
            return true;
        }
    }

    private void send(AiTaskMessage message) {
        try {
            aiTaskProducer.send(message);
        } catch (RuntimeException ex) {
            log.warn("AI task MQ publish failed: taskId={}, taskType={}",
                    message.taskId(), message.taskType(), ex);
        }
    }
}
