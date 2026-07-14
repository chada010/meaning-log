package com.chad.meaninglog.service.community;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 热榜分数计算: likes*2 + comments*3 + views*0.1 - hoursSincePublished * 0.5
 */
@Component
public class HotScoreCalculator {

    private static final double LIKE_WEIGHT = 2.0;
    private static final double COMMENT_WEIGHT = 3.0;
    private static final double VIEW_WEIGHT = 0.1;
    private static final double DECAY_PER_HOUR = 0.5;

    public double score(long likes, long comments, long views, LocalDateTime publishedAt) {
        double base = likes * LIKE_WEIGHT + comments * COMMENT_WEIGHT + views * VIEW_WEIGHT;
        long hours = hoursSince(publishedAt);
        return base - hours * DECAY_PER_HOUR;
    }

    private long hoursSince(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            return 0L;
        }
        Duration duration = Duration.between(publishedAt, LocalDateTime.now(ZoneId.systemDefault()));
        return Math.max(0L, duration.toHours());
    }
}
