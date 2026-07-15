package com.chad.meaninglog.service.community.job;

import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.repository.CommunityCounterBatchRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.service.community.CommunityRedisBatchService;
import com.chad.meaninglog.service.community.CommunityRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * dirty id 先进入带租约的 processing ZSet，单条批量 SQL 成功后再 ack。
 * DB 失败主动 requeue；进程中断则租约到期后自动回到 pending Set。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CounterFlushJob {

    private static final int BATCH_MAX = 100;
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(1);

    private final CommunityRedisService redis;
    private final CommunityRedisBatchService redisBatch;
    private final PublicLogRepository publicLogRepository;
    private final CommunityCounterBatchRepository counterBatchRepository;
    private final Clock businessClock;

    @Scheduled(
            fixedDelayString = "${community.counter-flush.fixed-delay-ms:5000}",
            initialDelayString = "${community.counter-flush.initial-delay-ms:5000}"
    )
    public void flush() {
        List<Long> ids = claim();
        if (ids.isEmpty()) {
            return;
        }
        try {
            List<PublicLog> publicLogs = publicLogRepository.selectByIds(ids);
            Map<Long, CommunityRedisBatchService.CommunityCounts> counts =
                    redisBatch.batchGetCounts(ids);
            List<CommunityCounterBatchRepository.ViewSnapshot> snapshots = new ArrayList<>();
            for (PublicLog publicLog : publicLogs) {
                CommunityRedisBatchService.CommunityCounts cached = counts.get(publicLog.getId());
                long cachedViews = cached == null ? 0L : cached.view();
                snapshots.add(new CommunityCounterBatchRepository.ViewSnapshot(
                        publicLog.getId(), Math.max(publicLog.getViewCount(), cachedViews)));
            }
            counterBatchRepository.updateViews(snapshots);
            redis.ackDirty(ids);
            log.debug("计数刷回完成, claimed={}, persisted={}", ids.size(), snapshots.size());
        } catch (DataAccessException exception) {
            retry(ids, exception);
        }
    }

    private List<Long> claim() {
        Instant now = businessClock.instant();
        List<String> raw;
        try {
            raw = redis.claimDirty(BATCH_MAX, now.toEpochMilli(),
                    now.plus(PROCESSING_LEASE).toEpochMilli());
        } catch (DataAccessException exception) {
            log.warn("刷回计数时 Redis 不可用, 本轮跳过", exception);
            return List.of();
        }
        return raw.stream().map(this::parseId).filter(Objects::nonNull).toList();
    }

    private Long parseId(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException exception) {
            log.warn("dirty counter 包含非法 publicLogId, 已忽略: {}", raw);
            redis.discardDirty(raw);
            return null;
        }
    }

    private void retry(List<Long> ids, DataAccessException failure) {
        log.warn("计数批量刷回失败, dirty id 将重试: count={}", ids.size(), failure);
        try {
            redis.retryDirty(ids);
        } catch (DataAccessException retryFailure) {
            log.warn("dirty id 主动回队失败, 将等待 processing 租约到期: count={}",
                    ids.size(), retryFailure);
        }
    }
}
