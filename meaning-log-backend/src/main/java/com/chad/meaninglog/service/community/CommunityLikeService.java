package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CommunityLikeService {

    private final PublicLogRepository publicLogRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommunityRedisRepairService repairService;
    private final NotificationService notificationService;
    private final Clock businessClock;

    public record LikeResult(boolean liked, long likeCount) {
    }

    @Transactional
    public LikeResult like(UserAccount user, Long publicLogId) {
        PublicLog publicLog = requireVisible(publicLogId);
        int inserted = postLikeRepository.insertIfAbsent(
                publicLogId, user.getId(), LocalDateTime.now(businessClock));
        if (inserted > 0) {
            int updated = publicLogRepository.incrementLikeCount(
                    publicLogId, LocalDateTime.now(businessClock));
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在或已下架");
            }
            notificationService.notifyLike(user, publicLog.getUserId(), publicLogId);
        }
        repairService.enqueuePostState(publicLogId, user.getId());
        return new LikeResult(true, postLikeRepository.countByPublicLogId(publicLogId));
    }

    @Transactional
    public LikeResult unlike(UserAccount user, Long publicLogId) {
        requireVisible(publicLogId);
        int deleted = postLikeRepository.deleteByPublicLogIdAndUserId(publicLogId, user.getId());
        if (deleted > 0) {
            publicLogRepository.decrementLikeCount(publicLogId, LocalDateTime.now(businessClock));
        }
        repairService.enqueuePostState(publicLogId, user.getId());
        return new LikeResult(false, postLikeRepository.countByPublicLogId(publicLogId));
    }

    private PublicLog requireVisible(Long publicLogId) {
        return publicLogRepository.findVisibleByIdForUpdate(publicLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在"));
    }
}
