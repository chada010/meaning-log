package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.dto.AiReportApplyRequest;
import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.dto.LogNavigationResponse;
import com.chad.meaninglog.dto.MeaningLogRequest;
import com.chad.meaninglog.dto.MeaningLogResponse;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeaningLogService {

    private final MeaningLogLifecycleService meaningLogLifecycleService;
    private final MeaningLogImageService meaningLogImageService;
    private final MeaningLogAiWorkflowService meaningLogAiWorkflowService;
    private final MeaningLogReportService meaningLogReportService;
    private final MeaningLogSupportService meaningLogSupportService;

    public List<MeaningLogResponse> findAll(UserAccount user, LocalDate logDate, String keyword, String tag, Boolean favorite) {
        return meaningLogLifecycleService.findAll(user, logDate, keyword, tag, favorite);
    }

    public MeaningLogResponse findById(UserAccount user, Long id) {
        return meaningLogLifecycleService.findById(user, id);
    }

    public LogNavigationResponse findNavigation(UserAccount user, Long id) {
        return meaningLogLifecycleService.findNavigation(user, id);
    }

    public MeaningLogResponse create(UserAccount user, MeaningLogRequest request) {
        return meaningLogLifecycleService.create(user, request);
    }

    public MeaningLogResponse update(UserAccount user, Long id, MeaningLogRequest request) {
        return meaningLogLifecycleService.update(user, id, request);
    }

    public void delete(UserAccount user, Long id) {
        meaningLogLifecycleService.delete(user, id);
    }

    public record AnalyzeStreamContext(MeaningLog log, List<LogImage> images) {}

    public List<String> findAiTags(UserAccount user) {
        return meaningLogAiWorkflowService.findAiTags(user);
    }

    public AnalyzeStreamContext prepareAnalyzeStream(UserAccount user, Long id) {
        return meaningLogAiWorkflowService.prepareAnalyzeStream(user, id);
    }

    public MeaningLogResponse generateAiForLog(UserAccount user, Long id) {
        return meaningLogAiWorkflowService.generateAiForLog(user, id);
    }

    public LogAiResult previewAiForLog(UserAccount user, Long id, String message) {
        return meaningLogAiWorkflowService.previewAiForLog(user, id, message);
    }

    public MeaningLogResponse applyAiForLog(UserAccount user, Long id, LogAiResult result) {
        return meaningLogAiWorkflowService.applyAiForLog(user, id, result);
    }

    public MeaningLogResponse updateFavorite(UserAccount user, Long id, boolean favorite) {
        return meaningLogLifecycleService.updateFavorite(user, id, favorite);
    }

    public AiReportResponse summarizeDay(UserAccount user, LocalDate date) {
        return meaningLogReportService.summarizeDay(user, date);
    }

    public AiReportResponse summarizePeriod(UserAccount user, LocalDate startDate, LocalDate endDate, String title) {
        return meaningLogReportService.summarizePeriod(user, startDate, endDate, title);
    }

    public List<AiReportResponse> findReports(UserAccount user) {
        return meaningLogReportService.findReports(user);
    }

    public AiReportResponse findReport(UserAccount user, Long id) {
        return meaningLogReportService.findReport(user, id);
    }

    public AiReportResponse applyReport(UserAccount user, Long id, AiReportApplyRequest request) {
        return meaningLogReportService.applyReport(user, id, request);
    }

    public AiReport getAiReport(UserAccount user, Long id) {
        return meaningLogSupportService.getAiReport(user, id);
    }

    public LogImage getLogImage(UserAccount user, Long imageId) {
        return meaningLogImageService.getLogImage(user, imageId);
    }

    public record ReportStreamContext(List<MeaningLog> logs, String period, AiReport.Type type,
                                      LocalDate startDate, LocalDate endDate) {}

    public ReportStreamContext prepareReportStream(
            UserAccount user, LocalDate startDate, LocalDate endDate, String title
    ) {
        return meaningLogReportService.prepareReportStream(user, startDate, endDate, title);
    }

    public ReportStreamContext prepareDailySummaryStream(UserAccount user, LocalDate date) {
        return meaningLogReportService.prepareDailySummaryStream(user, date);
    }

    public AiReportResponse saveReport(
            UserAccount user,
            AiReport.Type type,
            LocalDate startDate,
            LocalDate endDate,
            AiReportResponse response
    ) {
        return meaningLogReportService.saveReport(user, type, startDate, endDate, response);
    }
}
