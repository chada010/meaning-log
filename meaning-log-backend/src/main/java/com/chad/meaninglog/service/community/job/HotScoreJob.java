package com.chad.meaninglog.service.community.job;

import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.service.community.CommunityRedisBatchService;
import com.chad.meaninglog.service.community.CommunityRedisService;
import com.chad.meaninglog.service.community.HotScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class HotScoreJob {

    private static final int MAX_CANDIDATES = 5000;
    private static final long RECENT_DAYS = 7L;

    private final PublicLogRepository publicLogRepository;
    private final CommunityRedisService redis;
    private final CommunityRedisBatchService redisBatch;
    private final HotScoreCalculator hotScoreCalculator;
    private final Clock businessClock;

    @Scheduled(
            fixedDelayString = "${community.hot-score.fixed-delay-ms:300000}",
            initialDelayString = "${community.hot-score.initial-delay-ms:60000}"
    )
    public void recompute() {
        List<PublicLog> candidates = publicLogRepository.findRecentVisible(
                LocalDateTime.now(businessClock).minusDays(RECENT_DAYS), MAX_CANDIDATES);
        if (candidates.isEmpty()) {
            return;
        }
        Map<Long, Double> scoreById = new HashMap<>(candidates.size());
        for (PublicLog publicLog : candidates) {
            scoreById.put(publicLog.getId(), hotScoreCalculator.score(
                    publicLog.getLikeCount(), publicLog.getCommentCount(),
                    publicLog.getViewCount(), publicLog.getPublishedAt()));
        }
        try {
            redisBatch.batchUpdateHotScore(scoreById);
            redis.trimHot();
            log.debug("热榜重算完成, 候选数量: {}", candidates.size());
        } catch (DataAccessException exception) {
            log.warn("热榜重算时 Redis 不可用, 本轮跳过", exception);
        }
    }
}
