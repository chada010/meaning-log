package com.chad.meaninglog.service.community.job;

import com.chad.meaninglog.service.community.CommunityRedisRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityRedisRepairJob {

    private static final int BATCH_SIZE = 100;

    private final CommunityRedisRepairService repairService;

    @Scheduled(
            fixedDelayString = "${community.redis-repair.fixed-delay-ms:10000}",
            initialDelayString = "${community.redis-repair.initial-delay-ms:10000}"
    )
    public void repair() {
        try {
            int processed = repairService.processPending(BATCH_SIZE);
            if (processed > 0) {
                log.debug("社区 Redis repair 批次处理完成, 数量: {}", processed);
            }
        } catch (DataAccessException exception) {
            log.warn("读取社区 Redis repair 队列失败, 本轮保留等待重试", exception);
        }
    }
}
