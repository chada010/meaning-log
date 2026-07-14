package com.chad.meaninglog.service.community.job;

import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.service.community.CommunityRedisKeys;
import com.chad.meaninglog.service.community.CommunityRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * 每 5 秒把 Redis 计数刷回 MySQL。使用 dirty set 快速定位需要同步的 publicLogId, 避免全表扫描。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CounterFlushJob {

    private static final int BATCH_MAX = 100;

    private final CommunityRedisService redis;
    private final PublicLogRepository publicLogRepository;

    @Scheduled(fixedDelay = 5000L, initialDelay = 5000L)
    public void flush() {
        Set<String> dirtyIds;
        try {
            dirtyIds = redis.popDirty(BATCH_MAX);
        } catch (RedisConnectionFailureException e) {
            log.warn("刷回计数时 Redis 不可用, 本轮跳过");
            return;
        }
        if (dirtyIds.isEmpty()) {
            return;
        }
        for (String raw : dirtyIds) {
            Long id;
            try {
                id = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                continue;
            }
            Optional<PublicLog> opt = Optional.ofNullable(publicLogRepository.selectById(id));
            if (opt.isEmpty()) {
                continue;
            }
            long likes = redis.getCount(CommunityRedisKeys.countLike(id));
            long comments = redis.getCount(CommunityRedisKeys.countComment(id));
            long views = redis.getCount(CommunityRedisKeys.countView(id));
            publicLogRepository.updateCounters(id, Math.max(0, likes), Math.max(0, comments), Math.max(0, views));
        }
        log.debug("计数刷回完成, 数量: {}", dirtyIds.size());
    }
}
