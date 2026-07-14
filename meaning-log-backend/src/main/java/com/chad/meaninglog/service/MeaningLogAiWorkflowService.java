package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.dto.MeaningLogResponse;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MeaningLogAiWorkflowService {

    private final MeaningLogRepository meaningLogRepository;
    private final MeaningLogSupportService meaningLogSupportService;
    private final MeaningLogImageService meaningLogImageService;
    private final AiService aiService;

    @Transactional(readOnly = true)
    public List<String> findAiTags(UserAccount user) {
        return meaningLogRepository.findByUserOrderByLogDateDescCreatedAtDesc(user)
                .stream()
                .map(MeaningLog::getAiTags)
                .filter(Objects::nonNull)
                .flatMap(tags -> Arrays.stream(tags.split(",")))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public MeaningLogService.AnalyzeStreamContext prepareAnalyzeStream(UserAccount user, Long id) {
        MeaningLog meaningLog = meaningLogSupportService.getMeaningLog(user, id);
        return new MeaningLogService.AnalyzeStreamContext(meaningLog, meaningLogImageService.loadImages(meaningLog));
    }

    @Transactional
    public MeaningLogResponse generateAiForLog(UserAccount user, Long id) {
        MeaningLog meaningLog = meaningLogSupportService.getMeaningLog(user, id);
        LogAiResult result = aiService.analyzeLog(meaningLog, meaningLogImageService.loadImages(meaningLog));
        applyAiResult(meaningLog, result);
        meaningLogRepository.save(meaningLog);
        return meaningLogSupportService.toResponse(meaningLog);
    }

    @Transactional(readOnly = true)
    public LogAiResult previewAiForLog(UserAccount user, Long id, String message) {
        MeaningLog meaningLog = meaningLogSupportService.getMeaningLog(user, id);
        return aiService.refineLogSummary(
                meaningLog,
                List.of(),
                meaningLogImageService.loadImages(meaningLog),
                message
        );
    }

    @Transactional
    public MeaningLogResponse applyAiForLog(UserAccount user, Long id, LogAiResult result) {
        MeaningLog meaningLog = meaningLogSupportService.getMeaningLog(user, id);
        applyAiResult(meaningLog, result);
        meaningLogRepository.save(meaningLog);
        return meaningLogSupportService.toResponse(meaningLog);
    }

    private void applyAiResult(MeaningLog meaningLog, LogAiResult result) {
        meaningLog.setAiTitle(result.title());
        meaningLog.setAiSummary(result.summary());
        String tags = result.tags() == null
                ? ""
                : result.tags().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        meaningLog.setAiTags(tags);
    }
}
