package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.CommunityRedisRepair;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserFollow;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserFollowRepository;
import com.chad.meaninglog.time.BusinessTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityRedisRebuilder {

    private static final int MAX_FOLLOWERS_PUSH = 5000;

    private final PublicLogRepository publicLogRepository;
    private final MeaningLogRepository meaningLogRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserFollowRepository userFollowRepository;
    private final CommunityRedisService redis;
    private final HotScoreCalculator hotScoreCalculator;

    public void rebuild(CommunityRedisRepair repair) {
        CommunityRedisRepair.Type type = CommunityRedisRepair.Type.valueOf(repair.getRepairType());
        switch (type) {
            case POST_STATE -> rebuildPost(repair, false);
            case POST_PUBLISH -> rebuildPost(repair, true);
            case FOLLOW_STATE -> rebuildFollow(repair);
        }
    }

    private void rebuildPost(CommunityRedisRepair repair, boolean publishToFollowers) {
        Long publicLogId = repair.getAggregateId();
        PublicLog publicLog = publicLogRepository.selectById(publicLogId);
        if (!isVisibleWithSource(publicLog)) {
            redis.clearPostState(publicLogId);
            return;
        }

        Long userId = repair.getRelatedId();
        boolean liked = userId != null && postLikeRepository
                .findByPublicLogIdAndUserId(publicLogId, userId)
                .isPresent();
        double score = hotScoreCalculator.score(
                publicLog.getLikeCount(), publicLog.getCommentCount(),
                publicLog.getViewCount(), publicLog.getPublishedAt());
        boolean applied = redis.replacePostState(publicLogId, publicLog.getCacheVersion(),
                publicLog.getLikeCount(), publicLog.getCommentCount(), publicLog.getViewCount(),
                score, userId, liked);
        if (publishToFollowers && applied) {
            pushToFollowers(publicLog);
        }
    }

    private boolean isVisibleWithSource(PublicLog publicLog) {
        return publicLog != null
                && PublicLog.Status.VISIBLE.name().equals(publicLog.getStatus())
                && meaningLogRepository.selectById(publicLog.getLogId()) != null;
    }

    private void rebuildFollow(CommunityRedisRepair repair) {
        Long followerId = repair.getAggregateId();
        Long followeeId = repair.getRelatedId();
        boolean following = userFollowRepository
                .findByFollowerAndFollowee(followerId, followeeId)
                .isPresent();
        redis.replaceFollowState(repair.getId(), followerId, followeeId, following);
    }

    private void pushToFollowers(PublicLog publicLog) {
        List<UserFollow> followers = userFollowRepository.findFollowersOf(
                publicLog.getUserId(), MAX_FOLLOWERS_PUSH);
        if (followers.isEmpty()) {
            return;
        }
        long timestamp = publicLog.getPublishedAt()
                .atZone(BusinessTime.ZONE_ID)
                .toEpochSecond();
        redis.pushToFollowerFeeds(followers.stream().map(UserFollow::getFollowerId).toList(),
                publicLog.getId(), timestamp);
    }
}
