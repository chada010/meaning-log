package com.chad.meaninglog.service;

import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.repository.AiTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 恢复滞留的 PENDING 投递，并将超时 RUNNING 任务条件更新为 FAILED。
 * 两条扫描都限制批次；状态更新由数据库条件约束，多实例并发执行仍保持幂等。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "ai.task.reaper.enabled", havingValue = "true", matchIfMissing = true)
public class AiTaskReaper {

    private static final String TIMEOUT_MESSAGE = "running timeout";

    private final AiTaskRepository aiTaskRepository;
    private final AiTaskDeliveryService aiTaskDeliveryService;
    private final AiTaskNotifier aiTaskNotifier;

    @Value("${ai.task.running-timeout-seconds:600}")
    private long runningTimeoutSeconds;

    @Value("${ai.task.delivery.minimum-stale-seconds:30}")
    private long pendingStaleSeconds;

    @Value("${ai.task.delivery.batch-size:100}")
    private int deliveryBatchSize;

    @Value("${ai.task.reaper.batch-size:100}")
    private int reaperBatchSize;

    @Scheduled(fixedDelayString = "${ai.task.delivery.fixed-delay-ms:30000}")
    public void recoverStalePending() {
        LocalDateTime cutoff = AiTaskTime.now().minusSeconds(pendingStaleSeconds);
        List<AiTask> tasks = aiTaskRepository.findPendingForPublish(cutoff, deliveryBatchSize);
        int attempted = 0;
        for (AiTask task : tasks) {
            if (aiTaskDeliveryService.republishIfDue(task, cutoff)) {
                attempted++;
            }
        }
        if (attempted > 0) {
            log.info("AI task recovery attempted {} stale PENDING publishes (cutoff={})", attempted, cutoff);
        }
    }

    @Scheduled(fixedDelayString = "${ai.task.reaper.fixed-delay-ms:60000}")
    public void reapStaleRunning() {
        LocalDateTime failedAt = AiTaskTime.now();
        LocalDateTime cutoff = failedAt.minusSeconds(runningTimeoutSeconds);
        List<AiTask> tasks = aiTaskRepository.findStaleRunning(cutoff, reaperBatchSize);
        int reaped = 0;
        for (AiTask task : tasks) {
            int rows = aiTaskRepository.failStaleRunning(
                    task.getId(), cutoff, TIMEOUT_MESSAGE, failedAt);
            if (rows > 0) {
                reaped++;
                aiTaskNotifier.publishDone(task.getId());
            }
        }
        if (reaped > 0) {
            log.warn("AI task reaper marked {} stale RUNNING tasks as FAILED (cutoff={})", reaped, cutoff);
        }
    }
}
