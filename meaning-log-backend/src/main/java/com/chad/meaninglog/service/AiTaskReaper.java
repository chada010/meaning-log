package com.chad.meaninglog.service;

import com.chad.meaninglog.repository.AiTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 定时扫回 RUNNING 状态超时的任务：消费者进程若在 work.apply 之后、updateById(SUCCESS) 之前挂掉，
 * 任务会永远停在 RUNNING（既不是 PENDING 也不是终态，redeliver 也无法处理）。
 * 单条 UPDATE ... WHERE status='RUNNING' AND updated_at < cutoff 天然幂等，多实例并发运行也安全。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "ai.task.reaper.enabled", havingValue = "true", matchIfMissing = true)
public class AiTaskReaper {

    private static final String TIMEOUT_MESSAGE = "running timeout";

    private final AiTaskRepository aiTaskRepository;

    @Value("${ai.task.running-timeout-seconds:600}")
    private long runningTimeoutSeconds;

    @Scheduled(fixedDelayString = "${ai.task.reaper.fixed-delay-ms:60000}")
    public void reapStaleRunning() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(runningTimeoutSeconds);
        int reaped = aiTaskRepository.reapStaleRunning(cutoff, TIMEOUT_MESSAGE);
        if (reaped > 0) {
            log.warn("AI task reaper marked {} stale RUNNING tasks as FAILED (cutoff={})", reaped, cutoff);
        }
    }
}
