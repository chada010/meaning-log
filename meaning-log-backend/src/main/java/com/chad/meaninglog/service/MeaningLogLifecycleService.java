package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.LogNavigationResponse;
import com.chad.meaninglog.dto.MeaningLogRequest;
import com.chad.meaninglog.dto.MeaningLogResponse;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.service.community.CommunityPostLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MeaningLogLifecycleService {

    private final MeaningLogRepository meaningLogRepository;
    private final MeaningLogSupportService meaningLogSupportService;
    private final MeaningLogImageService meaningLogImageService;
    private final XiaojiChatService xiaojiChatService;
    private final CommunityPostLifecycleService communityPostLifecycleService;

    @Transactional(readOnly = true)
    public List<MeaningLogResponse> findAll(UserAccount user, LocalDate logDate, String keyword, String tag, Boolean favorite) {
        String normalizedKeyword = normalize(keyword);
        String normalizedTag = normalize(tag);
        List<MeaningLog> logs = logDate == null
                ? meaningLogRepository.findByUserOrderByLogDateDescCreatedAtDesc(user)
                : meaningLogRepository.findByUserAndLogDateOrderByCreatedAtDesc(user, logDate);

        return logs.stream()
                .filter(log -> matchesKeyword(log, normalizedKeyword))
                .filter(log -> matchesTag(log, normalizedTag))
                .filter(log -> favorite == null || log.isFavorite() == favorite)
                .map(MeaningLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MeaningLogResponse findById(UserAccount user, Long id) {
        return meaningLogSupportService.toResponse(meaningLogSupportService.getMeaningLog(user, id));
    }

    @Transactional(readOnly = true)
    public LogNavigationResponse findNavigation(UserAccount user, Long id) {
        List<MeaningLog> logs = meaningLogRepository.findByUserOrderByLogDateDescCreatedAtDesc(user);
        int index = -1;
        for (int i = 0; i < logs.size(); i++) {
            if (Objects.equals(logs.get(i).getId(), id)) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found");
        }

        MeaningLogResponse previous = index > 0 ? MeaningLogResponse.from(logs.get(index - 1)) : null;
        MeaningLogResponse next = index < logs.size() - 1 ? MeaningLogResponse.from(logs.get(index + 1)) : null;
        return new LogNavigationResponse(previous, next);
    }

    @Transactional
    public MeaningLogResponse create(UserAccount user, MeaningLogRequest request) {
        MeaningLog meaningLog = new MeaningLog();
        meaningLog.setUser(user);
        applyRequest(meaningLog, request);
        MeaningLog savedLog = meaningLogRepository.save(meaningLog);
        meaningLogImageService.replaceImages(savedLog, request.getImages());
        return meaningLogSupportService.toResponse(savedLog);
    }

    @Transactional
    public MeaningLogResponse update(UserAccount user, Long id, MeaningLogRequest request) {
        MeaningLog meaningLog = meaningLogSupportService.getMeaningLog(user, id);
        applyRequest(meaningLog, request);
        meaningLogRepository.save(meaningLog);
        meaningLogImageService.replaceImages(meaningLog, request.getImages());
        return meaningLogSupportService.toResponse(meaningLog);
    }

    @Transactional
    public void delete(UserAccount user, Long id) {
        MeaningLog meaningLog = meaningLogSupportService.getMeaningLogForUpdate(user, id);
        communityPostLifecycleService.deleteForMeaningLog(meaningLog.getId());
        meaningLogImageService.deleteImages(meaningLog);
        xiaojiChatService.deleteLogChats(meaningLog);
        meaningLogRepository.delete(meaningLog);
    }

    @Transactional
    public MeaningLogResponse updateFavorite(UserAccount user, Long id, boolean favorite) {
        MeaningLog meaningLog = meaningLogSupportService.getMeaningLog(user, id);
        meaningLog.setFavorite(favorite);
        meaningLogRepository.save(meaningLog);
        return MeaningLogResponse.from(meaningLog);
    }

    private void applyRequest(MeaningLog meaningLog, MeaningLogRequest request) {
        meaningLog.setTitle(request.getTitle());
        meaningLog.setContent(request.getContent());
        meaningLog.setLogDate(request.getLogDate());
        meaningLog.setMood(request.getMood());
        if (request.getFavorite() != null) {
            meaningLog.setFavorite(request.getFavorite());
        }
    }

    private boolean matchesKeyword(MeaningLog log, String keyword) {
        if (keyword == null) {
            return true;
        }

        return contains(log.getTitle(), keyword)
                || contains(log.getContent(), keyword)
                || contains(log.getMood(), keyword)
                || contains(log.getAiTitle(), keyword)
                || contains(log.getAiSummary(), keyword)
                || contains(log.getAiTags(), keyword);
    }

    private boolean matchesTag(MeaningLog log, String tag) {
        if (tag == null) {
            return true;
        }

        return log.getAiTags() != null
                && Arrays.stream(log.getAiTags().split(","))
                .map(String::trim)
                .anyMatch(existingTag -> existingTag.equalsIgnoreCase(tag));
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }
}
