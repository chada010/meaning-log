package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.MeaningLogResponse;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.AiReportRepository;
import com.chad.meaninglog.repository.MeaningLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MeaningLogSupportService {

    private final MeaningLogRepository meaningLogRepository;
    private final AiReportRepository aiReportRepository;
    private final MeaningLogImageService meaningLogImageService;

    @Transactional(readOnly = true)
    public MeaningLog getMeaningLog(UserAccount user, Long id) {
        return meaningLogRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found"));
    }

    @Transactional(readOnly = true)
    public AiReport getAiReport(UserAccount user, Long id) {
        return aiReportRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI report not found"));
    }

    @Transactional(readOnly = true)
    public MeaningLogResponse toResponse(MeaningLog log) {
        return MeaningLogResponse.from(log, meaningLogImageService.loadImages(log));
    }
}
