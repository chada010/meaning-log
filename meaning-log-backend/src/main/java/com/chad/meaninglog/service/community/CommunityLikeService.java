package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.PostLike;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityLikeService {

    private final PublicLogRepository publicLogRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommunityRedisService redis;
    private final HotScoreCalculator hotScoreCalculator;
    private final NotificationService notificationService;

    public record LikeResult(boolean liked, long likeCount) {}

    @Transactional
    public LikeResult like(UserAccount user, Long publicLogId) {
        PublicLog publicLog = publicLogRepository.findVisibleById(publicLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在"));

        if (redis.hasLiked(publicLogId, user.getId())) {
            return new LikeResult(true, redis.getCount(CommunityRedisKeys.countLike(publicLogId)));
        }

        redis.markLiked(publicLogId, user.getId());
        long likes = redis.incrLike(publicLogId);
        updateHotScore(publicLog, likes);

        PostLike record = new PostLike();
        record.setPublicLogId(publicLogId);
        record.setUserId(user.getId());
        try {
            postLikeRepository.insert(record);
        } catch (DuplicateKeyException ignore) {
            // 由唯一索引兜底, 已有记录直接放行
        }
        notificationService.notifyLike(user, publicLog.getUserId(), publicLogId);
        return new LikeResult(true, likes);
    }

    @Transactional
    public LikeResult unlike(UserAccount user, Long publicLogId) {
        PublicLog publicLog = publicLogRepository.findVisibleById(publicLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在"));

        if (!redis.hasLiked(publicLogId, user.getId())) {
            return new LikeResult(false, redis.getCount(CommunityRedisKeys.countLike(publicLogId)));
        }

        redis.markUnliked(publicLogId, user.getId());
        long likes = Math.max(0, redis.decrLike(publicLogId));
        updateHotScore(publicLog, likes);
        postLikeRepository.deleteByPublicLogIdAndUserId(publicLogId, user.getId());
        return new LikeResult(false, likes);
    }

    private void updateHotScore(PublicLog publicLog, long likes) {
        long comments = redis.getCount(CommunityRedisKeys.countComment(publicLog.getId()));
        long views = redis.getCount(CommunityRedisKeys.countView(publicLog.getId()));
        double score = hotScoreCalculator.score(likes, comments, views, publicLog.getPublishedAt());
        redis.updateHotScore(publicLog.getId(), score);
    }
}
