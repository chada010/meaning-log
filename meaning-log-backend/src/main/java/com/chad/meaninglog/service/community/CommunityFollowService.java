package com.chad.meaninglog.service.community;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chad.meaninglog.dto.community.UserProfileResponse;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.repository.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CommunityFollowService {

    private final UserFollowRepository userFollowRepository;
    private final UserAccountRepository userAccountRepository;
    private final PublicLogRepository publicLogRepository;
    private final CommunityRedisRepairService repairService;
    private final NotificationService notificationService;
    private final Clock businessClock;

    @Transactional
    public boolean follow(UserAccount follower, Long followeeId) {
        if (follower.getId().equals(followeeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能关注自己");
        }
        if (userAccountRepository.selectById(followeeId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        int inserted = userFollowRepository.insertIfAbsent(follower.getId(), followeeId,
                LocalDateTime.now(businessClock));
        if (inserted > 0) {
            notificationService.notifyFollow(follower, followeeId);
        }
        repairService.enqueueFollowState(follower.getId(), followeeId);
        return inserted > 0;
    }

    @Transactional
    public boolean unfollow(UserAccount follower, Long followeeId) {
        int rows = userFollowRepository.deleteByFollowerAndFollowee(follower.getId(), followeeId);
        repairService.enqueueFollowState(follower.getId(), followeeId);
        return rows > 0;
    }

    public UserProfileResponse profile(UserAccount viewer, Long userId) {
        UserAccount target = userAccountRepository.selectById(userId);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        boolean self = viewer.getId().equals(userId);
        boolean following = !self && userFollowRepository
                .findByFollowerAndFollowee(viewer.getId(), userId).isPresent();
        long followerCount = userFollowRepository.countFollowers(userId);
        long followingCount = userFollowRepository.countFollowing(userId);
        long postCount = publicLogRepository.selectCount(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getUserId, userId)
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name()));
        return UserProfileResponse.from(target, followerCount, followingCount,
                postCount, following, self);
    }
}
