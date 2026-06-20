package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.dto.AiReportApplyRequest;
import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.dto.LogImageRequest;
import com.chad.meaninglog.dto.LogNavigationResponse;
import com.chad.meaninglog.dto.MeaningLogRequest;
import com.chad.meaninglog.dto.MeaningLogResponse;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.AiReportRepository;
import com.chad.meaninglog.repository.LogImageRepository;
import com.chad.meaninglog.repository.MeaningLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class MeaningLogService {

    private final MeaningLogRepository meaningLogRepository;
    private final AiReportRepository aiReportRepository;
    private final LogImageRepository logImageRepository;
    private final AiService aiService;
    private final AiRateLimiter aiRateLimiter;

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
        return toResponse(getMeaningLog(user, id));
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

    @Transactional
    public MeaningLogResponse create(UserAccount user, MeaningLogRequest request) {
        MeaningLog meaningLog = new MeaningLog();
        meaningLog.setUser(user);
        applyRequest(meaningLog, request);
        MeaningLog savedLog = meaningLogRepository.save(meaningLog);
        replaceImages(savedLog, request.getImages());
        return toResponse(savedLog);
    }

    @Transactional
    public MeaningLogResponse update(UserAccount user, Long id, MeaningLogRequest request) {
        MeaningLog meaningLog = getMeaningLog(user, id);
        applyRequest(meaningLog, request);
        meaningLogRepository.save(meaningLog);
        replaceImages(meaningLog, request.getImages());
        return toResponse(meaningLog);
    }

    @Transactional
    public void delete(UserAccount user, Long id) {
        MeaningLog meaningLog = getMeaningLog(user, id);
        logImageRepository.deleteByMeaningLog(meaningLog);
        meaningLogRepository.delete(meaningLog);
    }

    public record AnalyzeStreamContext(MeaningLog log, List<LogImage> images) {}

    @Transactional(readOnly = true)
    public AnalyzeStreamContext prepareAnalyzeStream(UserAccount user, Long id) {
        aiRateLimiter.check(user);
        MeaningLog meaningLog = getMeaningLog(user, id);
        List<LogImage> images = logImageRepository.findByMeaningLogOrderByDisplayOrderAscIdAsc(meaningLog);
        return new AnalyzeStreamContext(meaningLog, images);
    }

    @Transactional
    public MeaningLogResponse generateAiForLog(UserAccount user, Long id) {
        aiRateLimiter.check(user);
        MeaningLog meaningLog = getMeaningLog(user, id);
        LogAiResult result = aiService.analyzeLog(
                meaningLog,
                logImageRepository.findByMeaningLogOrderByDisplayOrderAscIdAsc(meaningLog)
        );
        applyAiResult(meaningLog, result);
        meaningLogRepository.save(meaningLog);
        return toResponse(meaningLog);
    }

    @Transactional(readOnly = true)
    public LogAiResult previewAiForLog(UserAccount user, Long id, String message) {
        aiRateLimiter.check(user);
        MeaningLog meaningLog = getMeaningLog(user, id);
        return aiService.refineLogSummary(
                meaningLog,
                List.of(),
                logImageRepository.findByMeaningLogOrderByDisplayOrderAscIdAsc(meaningLog),
                message
        );
    }

    @Transactional
    public MeaningLogResponse applyAiForLog(UserAccount user, Long id, LogAiResult result) {
        MeaningLog meaningLog = getMeaningLog(user, id);
        applyAiResult(meaningLog, result);
        meaningLogRepository.save(meaningLog);
        return toResponse(meaningLog);
    }

    @Transactional
    public MeaningLogResponse updateFavorite(UserAccount user, Long id, boolean favorite) {
        MeaningLog meaningLog = getMeaningLog(user, id);
        meaningLog.setFavorite(favorite);
        meaningLogRepository.save(meaningLog);
        return MeaningLogResponse.from(meaningLog);
    }

    @Transactional
    public AiReportResponse summarizeDay(UserAccount user, LocalDate date) {
        aiRateLimiter.check(user);
        List<MeaningLog> logs = meaningLogRepository.findByUserAndLogDateOrderByCreatedAtDesc(user, date);
        AiReportResponse response = aiService.summarizeLogs("AI 当天总结", date.toString(), logs);
        return saveReport(user, AiReport.Type.DAILY, date, date, response);
    }

    @Transactional
    public AiReportResponse summarizePeriod(UserAccount user, LocalDate startDate, LocalDate endDate, String title) {
        aiRateLimiter.check(user);
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
        return AiReportResponse.from(getAiReport(user, id));
    }

    @Transactional
    public AiReportResponse applyReport(UserAccount user, Long id, AiReportApplyRequest request) {
        AiReport report = getAiReport(user, id);
        report.setTitle(blankToFallback(request.getTitle(), report.getTitle()));
        report.setPeriod(blankToFallback(request.getPeriod(), report.getPeriod()));
        report.setSummary(blankToFallback(request.getSummary(), report.getSummary()));
        report.setTags(request.getTags() == null ? "" : request.getTags().trim());
        return AiReportResponse.from(aiReportRepository.save(report));
    }

    private MeaningLog getMeaningLog(UserAccount user, Long id) {
        return meaningLogRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Log not found"));
    }

    public AiReport getAiReport(UserAccount user, Long id) {
        return aiReportRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI report not found"));
    }

    @Transactional(readOnly = true)
    public LogImage getLogImage(UserAccount user, Long imageId) {
        return logImageRepository.findByIdAndMeaningLogUser(imageId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
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

    private void replaceImages(MeaningLog meaningLog, List<LogImageRequest> images) {
        logImageRepository.deleteByMeaningLog(meaningLog);
        if (images == null || images.isEmpty()) {
            return;
        }

        for (int index = 0; index < images.size(); index++) {
            LogImageRequest imageRequest = images.get(index);
            ParsedImage parsedImage = parseImage(imageRequest);
            LogImage image = new LogImage();
            image.setMeaningLog(meaningLog);
            image.setFileName(blankToFallback(imageRequest.getFileName(), "log-image-" + (index + 1)));
            image.setCaption(imageRequest.getCaption() == null ? "" : imageRequest.getCaption().trim());
            image.setContentType(parsedImage.contentType());
            image.setFileSize(parsedImage.data().length);
            image.setDisplayOrder(index);
            image.setData(parsedImage.data());
            logImageRepository.save(image);
        }
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

    public record ReportStreamContext(List<MeaningLog> logs, String period, AiReport.Type type,
                                      LocalDate startDate, LocalDate endDate) {}

    public ReportStreamContext prepareReportStream(
            UserAccount user, LocalDate startDate, LocalDate endDate, String title
    ) {
        aiRateLimiter.check(user);
        List<MeaningLog> logs = meaningLogRepository.findByUserAndLogDateBetweenOrderByLogDateDesc(user, startDate, endDate);
        return new ReportStreamContext(logs, startDate + " 至 " + endDate, inferReportType(title), startDate, endDate);
    }

    public ReportStreamContext prepareDailySummaryStream(UserAccount user, LocalDate date) {
        aiRateLimiter.check(user);
        List<MeaningLog> logs = meaningLogRepository.findByUserAndLogDateOrderByCreatedAtDesc(user, date);
        return new ReportStreamContext(logs, date.toString(), AiReport.Type.DAILY, date, date);
    }

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

    private MeaningLogResponse toResponse(MeaningLog log) {
        return MeaningLogResponse.from(log, logImageRepository.findByMeaningLogOrderByDisplayOrderAscIdAsc(log));
    }

    private ParsedImage parseImage(LogImageRequest request) {
        if (request == null || request.getDataUrl() == null || request.getDataUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片内容不能为空");
        }

        String dataUrl = request.getDataUrl().trim();
        int commaIndex = dataUrl.indexOf(',');
        String metadata = commaIndex > 0 ? dataUrl.substring(0, commaIndex) : "";
        String base64 = commaIndex > 0 ? dataUrl.substring(commaIndex + 1) : dataUrl;
        String contentType = request.getContentType();

        if (metadata.startsWith("data:") && metadata.contains(";base64")) {
            contentType = metadata.substring("data:".length(), metadata.indexOf(";base64"));
        }

        if (contentType == null || !contentType.matches("image/(png|jpeg|jpg|webp|gif)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PNG、JPG、WEBP、GIF 图片");
        }

        byte[] data;
        try {
            data = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片内容不是有效的 Base64", ex);
        }

        if (data.length > 2 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单张图片不能超过 2MB");
        }

        return new ParsedImage(contentType.equals("image/jpg") ? "image/jpeg" : contentType, data);
    }

    private record ParsedImage(String contentType, byte[] data) {
    }
}
