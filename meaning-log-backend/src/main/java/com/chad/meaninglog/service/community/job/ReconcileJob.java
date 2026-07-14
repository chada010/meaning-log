package com.chad.meaninglog.service.community.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.service.community.CommunityRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每小时对账: Redis Bitmap 点赞数 vs post_likes DB 记录数, 差异 > 5% 记 warn。
 * MVP 阶段只观察不修复, 后续可根据 warn 告警手工介入或加自动修复。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconcileJob {

    private static final int MAX_CHECK = 200;
    private static final double THRESHOLD = 0.05;
    private static final long RECENT_DAYS = 30L;

    private final PublicLogRepository publicLogRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommunityRedisService redis;

    @Scheduled(cron = "${community.reconcile.cron:17 3 * * * *}")
    public void reconcile() {
        try {
            List<PublicLog> candidates = publicLogRepository.selectList(new LambdaQueryWrapper<PublicLog>()
                    .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())
                    .ge(PublicLog::getPublishedAt, LocalDateTime.now().minusDays(RECENT_DAYS))
                    .orderByDesc(PublicLog::getPublishedAt)
                    .last("LIMIT " + MAX_CHECK));
            int mismatched = 0;
            for (PublicLog p : candidates) {
                long bitmapCount = redis.bitmapLikeCount(p.getId());
                long dbCount = postLikeRepository.countByPublicLogId(p.getId());
                if (dbCount == 0 && bitmapCount == 0) {
                    continue;
                }
                double diffRatio = Math.abs(bitmapCount - dbCount) / (double) Math.max(1, dbCount);
                if (diffRatio > THRESHOLD) {
                    mismatched++;
                    log.warn("点赞对账差异: publicLogId={}, bitmap={}, db={}, diff={}%",
                            p.getId(), bitmapCount, dbCount, Math.round(diffRatio * 100));
                }
            }
            log.info("对账完成: 检查 {} 条, 差异 {} 条", candidates.size(), mismatched);
        } catch (RedisConnectionFailureException e) {
            log.warn("对账时 Redis 不可用, 本轮跳过");
        }
    }
}
