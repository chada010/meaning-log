package com.chad.meaninglog.service.community.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.service.community.CommunityRedisKeys;
import com.chad.meaninglog.service.community.CommunityRedisService;
import com.chad.meaninglog.service.community.HotScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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
            for (PublicLog p : candidates) {
                long likes = redis.getCount(CommunityRedisKeys.countLike(p.getId()));
                long comments = redis.getCount(CommunityRedisKeys.countComment(p.getId()));
                long views = redis.getCount(CommunityRedisKeys.countView(p.getId()));
                double score = hotScoreCalculator.score(likes, comments, views, p.getPublishedAt());
                redis.updateHotScore(p.getId(), score);
            }
            redis.trimHot();
            log.debug("热榜重算完成, 候选数量: {}", candidates.size());
        } catch (RedisConnectionFailureException e) {
            log.warn("热榜重算时 Redis 不可用, 本轮跳过");
        }
    }
}
