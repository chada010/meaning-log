package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.AiReportApplyRequest;
import com.chad.meaninglog.dto.AiReportResponse;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeaningLogReportService {

    private final MeaningLogRepository meaningLogRepository;
    private final AiReportRepository aiReportRepository;
    private final MeaningLogSupportService meaningLogSupportService;
    private final AiService aiService;

    @Transactional
    public AiReportResponse summarizeDay(UserAccount user, LocalDate date) {
        date = validateDailySummaryDate(date);
        List<MeaningLog> logs = meaningLogRepository.findByUserAndLogDateOrderByCreatedAtDesc(user, date);
        AiReportResponse response = aiService.summarizeLogs("AI 当天总结", date.toString(), logs);
        return saveReport(user, AiReport.Type.DAILY, date, date, response);
    }

    @Transactional
    public AiReportResponse summarizePeriod(UserAccount user, LocalDate startDate, LocalDate endDate, String title) {
        validateReportDateRange(startDate, endDate);
        List<MeaningLog> logs = meaningLogRepository.findByUserAndLogDateBetweenOrderByLogDateDesc(user, startDate, endDate);
        AiReportResponse response = aiService.summarizeLogs(title, startDate + " 至 " + endDate, logs);
        return saveReport(user, inferReportType(title), startDate, endDate, response);
    }

    @Transactional(readOnly = true)
    public List<AiReportResponse> findReports(UserAccount user) {
        return aiReportRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(AiReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiReportResponse findReport(UserAccount user, Long id) {
        return AiReportResponse.from(meaningLogSupportService.getAiReport(user, id));
    }

    @Transactional
    public AiReportResponse applyReport(UserAccount user, Long id, AiReportApplyRequest request) {
        AiReport report = meaningLogSupportService.getAiReport(user, id);
        report.setTitle(blankToFallback(request.getTitle(), report.getTitle()));
        report.setPeriod(blankToFallback(request.getPeriod(), report.getPeriod()));
        report.setSummary(blankToFallback(request.getSummary(), report.getSummary()));
        report.setTags(request.getTags() == null ? "" : request.getTags().trim());
        return AiReportResponse.from(aiReportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public MeaningLogService.ReportStreamContext prepareReportStream(
            UserAccount user, LocalDate startDate, LocalDate endDate, String title
    ) {
        validateReportDateRange(startDate, endDate);
        List<MeaningLog> logs = meaningLogRepository.findByUserAndLogDateBetweenOrderByLogDateDesc(user, startDate, endDate);
        return new MeaningLogService.ReportStreamContext(
                logs,
                startDate + " 至 " + endDate,
                inferReportType(title),
                startDate,
                endDate
        );
    }

    @Transactional(readOnly = true)
    public MeaningLogService.ReportStreamContext prepareDailySummaryStream(UserAccount user, LocalDate date) {
        date = validateDailySummaryDate(date);
        List<MeaningLog> logs = meaningLogRepository.findByUserAndLogDateOrderByCreatedAtDesc(user, date);
        return new MeaningLogService.ReportStreamContext(logs, date.toString(), AiReport.Type.DAILY, date, date);
    }

    @Transactional
    public AiReportResponse saveReport(
            UserAccount user,
            AiReport.Type type,
            LocalDate startDate,
            LocalDate endDate,
            AiReportResponse response
    ) {
        AiReport report = new AiReport();
        report.setUser(user);
        report.setType(type);
        report.setTitle(response.getTitle());
        report.setPeriod(response.getPeriod());
        report.setStartDate(startDate);
        report.setEndDate(endDate);
        report.setSummary(response.getSummary());
        report.setTags(response.getTags());
        return AiReportResponse.from(aiReportRepository.save(report));
    }

    public LocalDate parseDailySummaryDate(String date) {
        return validateDailySummaryDate(parseRequiredDate(date, "date"));
    }

    public MeaningLogService.ReportDateRange parseReportDateRange(String startDate, String endDate) {
        return validateReportDateRange(
                parseRequiredDate(startDate, "startDate"),
                parseRequiredDate(endDate, "endDate")
        );
    }

    private LocalDate validateDailySummaryDate(LocalDate date) {
        if (date == null) {
            throw badRequest("date is required");
        }
        return date;
    }

    private MeaningLogService.ReportDateRange validateReportDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw badRequest("startDate is required");
        }
        if (endDate == null) {
            throw badRequest("endDate is required");
        }
        if (startDate.isAfter(endDate)) {
            throw badRequest("startDate must not be after endDate");
        }
        return new MeaningLogService.ReportDateRange(startDate, endDate);
    }

    private LocalDate parseRequiredDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw badRequest(fieldName + " is required");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw badRequest(fieldName + " must use YYYY-MM-DD format");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private AiReport.Type inferReportType(String title) {
        if (title != null && title.contains("周")) {
            return AiReport.Type.WEEKLY;
        }
        if (title != null && title.contains("月")) {
            return AiReport.Type.MONTHLY;
        }
        return AiReport.Type.CUSTOM;
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
