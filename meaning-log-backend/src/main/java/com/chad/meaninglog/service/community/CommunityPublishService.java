package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
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
public class CommunityPublishService {

    private final PublicLogRepository publicLogRepository;
    private final MeaningLogRepository meaningLogRepository;
    private final CommunityRedisRepairService repairService;
    private final Clock businessClock;

    @Transactional
    public PublicLog publish(UserAccount user, Long logId) {
        MeaningLog log = meaningLogRepository.findByIdAndUserForUpdate(logId, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "日志不存在或无权限"));
        PublicLog publicLog = publicLogRepository.findByLogIdForUpdate(logId)
                .map(existing -> restoreIfHidden(existing, user))
                .orElseGet(() -> createNew(log, user));
        repairService.enqueuePostPublish(publicLog.getId());
        return publicLog;
    }

    private PublicLog restoreIfHidden(PublicLog existing, UserAccount user) {
        if (!existing.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权发布他人日志");
        }
        if (PublicLog.Status.VISIBLE.name().equals(existing.getStatus())) {
            return existing;
        }
        existing.setStatus(PublicLog.Status.VISIBLE.name());
        existing.setPublishedAt(LocalDateTime.now(businessClock));
        existing.setCacheVersion(existing.getCacheVersion() + 1);
        return publicLogRepository.save(existing);
    }

    private PublicLog createNew(MeaningLog log, UserAccount user) {
        PublicLog publicLog = new PublicLog();
        publicLog.setLogId(log.getId());
        publicLog.setUserId(user.getId());
        publicLog.setPublishedAt(LocalDateTime.now(businessClock));
        publicLog.setStatus(PublicLog.Status.VISIBLE.name());
        publicLog.setCacheVersion(1L);
        return publicLogRepository.save(publicLog);
    }

    @Transactional
    public void unpublish(UserAccount user, Long logId) {
        meaningLogRepository.findByIdAndUserForUpdate(logId, user)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "日志不存在或无权限"));
        PublicLog publicLog = publicLogRepository.findByLogIdForUpdate(logId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该日志未发布"));
        if (!publicLog.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权撤回他人发布");
        }
        publicLog.setStatus(PublicLog.Status.HIDDEN.name());
        publicLog.setCacheVersion(publicLog.getCacheVersion() + 1);
        publicLogRepository.save(publicLog);
        repairService.enqueuePostState(publicLog.getId(), null);
    }
}
