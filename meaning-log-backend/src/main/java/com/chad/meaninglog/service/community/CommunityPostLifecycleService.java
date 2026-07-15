package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.repository.NotificationRepository;
import com.chad.meaninglog.repository.PostCommentRepository;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommunityPostLifecycleService {

    private final PublicLogRepository publicLogRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final NotificationRepository notificationRepository;
    private final CommunityRedisRepairService repairService;

    public void deleteForMeaningLog(Long meaningLogId) {
        PublicLog publicLog = publicLogRepository.findByLogId(meaningLogId).orElse(null);
        if (publicLog == null) {
            return;
        }
        Long publicLogId = publicLog.getId();
        notificationRepository.deleteByPublicLogId(publicLogId);
        postLikeRepository.deleteByPublicLogId(publicLogId);
        postCommentRepository.deleteByPublicLogId(publicLogId);
        publicLogRepository.deleteById(publicLogId);
        repairService.enqueuePostState(publicLogId, null);
    }
}
