package com.chad.meaninglog.service.community.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.service.community.CommunityRedisService;
import com.chad.meaninglog.service.community.HotScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 每 5 分钟重算热榜前 1000。基于 published_at 最近 7 天的可见帖子。
 * 时间衰减保证老帖子会自然掉出榜单。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HotScoreJob {

    private static final int MAX_CANDIDATES = 5000;
    private static final long RECENT_DAYS = 7L;

    private final PublicLogRepository publicLogRepository;
    private final CommunityRedisService redis;
    private final HotScoreCalculator hotScoreCalculator;

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void recompute() {
        try {
            List<PublicLog> candidates = publicLogRepository.selectList(new LambdaQueryWrapper<PublicLog>()
                    .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())
                    .ge(PublicLog::getPublishedAt, LocalDateTime.now().minusDays(RECENT_DAYS))
                    .last("LIMIT " + MAX_CANDIDATES));
            if (candidates.isEmpty()) {
                return;
            }
            // 一次 MGET 拉齐 K 篇帖子的 3K 个 counter, 再一次 pipeline ZADD 写回,
            // K 篇候选帖子从原来的 4K 次 RTT (3K getCount + K updateHotScore) 降为 2 次 RTT
            List<Long> ids = candidates.stream().map(PublicLog::getId).collect(Collectors.toList());
            Map<Long, CommunityRedisService.CommunityCounts> countsMap = redis.batchGetCounts(ids);
            Map<Long, Double> scoreById = new HashMap<>(candidates.size());
            for (PublicLog p : candidates) {
                CommunityRedisService.CommunityCounts c = countsMap.getOrDefault(
                        p.getId(), new CommunityRedisService.CommunityCounts(0L, 0L, 0L));
                double score = hotScoreCalculator.score(c.like(), c.comment(), c.view(), p.getPublishedAt());
                scoreById.put(p.getId(), score);
            }
            redis.batchUpdateHotScore(scoreById);
            redis.trimHot();
            log.debug("热榜重算完成, 候选数量: {}", candidates.size());
        } catch (RedisConnectionFailureException e) {
            log.warn("热榜重算时 Redis 不可用, 本轮跳过");
        }
    }
}
