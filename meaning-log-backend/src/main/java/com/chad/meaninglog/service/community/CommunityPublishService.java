package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.entity.UserFollow;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityPublishService {

    private static final Duration PUBLISH_LOCK_TTL = Duration.ofSeconds(5);
    private static final int MAX_FOLLOWERS_PUSH = 5000;

    private final PublicLogRepository publicLogRepository;
    private final MeaningLogRepository meaningLogRepository;
    private final UserFollowRepository userFollowRepository;
    private final CommunityRedisService redis;
    private final HotScoreCalculator hotScoreCalculator;

    @Transactional
    public PublicLog publish(UserAccount user, Long logId) {
        MeaningLog log = meaningLogRepository.findByIdAndUser(logId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "日志不存在或无权限"));

        String lockKey = CommunityRedisKeys.publishLock(user.getId());
        String token = redis.acquireLock(lockKey, PUBLISH_LOCK_TTL);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "操作过于频繁, 请稍后再试");
        }
        try {
            return publicLogRepository.findByLogId(logId)
                    .map(existing -> restoreIfHidden(existing, user))
                    .orElseGet(() -> createNew(log, user));
        } finally {
            redis.releaseLock(lockKey, token);
        }
    }

    private PublicLog restoreIfHidden(PublicLog existing, UserAccount user) {
        if (!existing.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权发布他人日志");
        }
        if (PublicLog.Status.VISIBLE.name().equals(existing.getStatus())) {
            return existing;
        }
        existing.setStatus(PublicLog.Status.VISIBLE.name());
        existing.setPublishedAt(LocalDateTime.now(ZoneId.systemDefault()));
        publicLogRepository.save(existing);
        afterPublish(existing, user);
        return existing;
    }

    private PublicLog createNew(MeaningLog log, UserAccount user) {
        PublicLog publicLog = new PublicLog();
        publicLog.setLogId(log.getId());
        publicLog.setUserId(user.getId());
        publicLog.setPublishedAt(LocalDateTime.now(ZoneId.systemDefault()));
        publicLog.setStatus(PublicLog.Status.VISIBLE.name());
        publicLogRepository.save(publicLog);
        afterPublish(publicLog, user);
        return publicLog;
    }

    private void afterPublish(PublicLog publicLog, UserAccount user) {
        redis.seedCounts(publicLog.getId(),
                publicLog.getLikeCount(),
                publicLog.getCommentCount(),
                publicLog.getViewCount());
        redis.updateHotScore(publicLog.getId(),
                hotScoreCalculator.score(0, 0, 0, publicLog.getPublishedAt()));
        pushToFollowers(publicLog, user);
    }

    private void pushToFollowers(PublicLog publicLog, UserAccount user) {
        List<UserFollow> followers = userFollowRepository.findFollowersOf(user.getId(), MAX_FOLLOWERS_PUSH);
        if (followers.isEmpty()) {
            return;
        }
        long ts = publicLog.getPublishedAt().toEpochSecond(ZoneOffset.UTC);
        List<Long> followerIds = followers.stream().map(UserFollow::getFollowerId).toList();
        redis.pushToFollowerFeeds(followerIds, publicLog.getId(), ts);
    }

    @Transactional
    public void unpublish(UserAccount user, Long logId) {
        PublicLog publicLog = publicLogRepository.findByLogId(logId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该日志未发布"));
        if (!publicLog.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权撤回他人发布");
        }
        publicLog.setStatus(PublicLog.Status.HIDDEN.name());
        publicLogRepository.save(publicLog);
        redis.removeFromHot(publicLog.getId());
    }
}
