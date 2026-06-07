package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiApplyRequest;
import com.chad.meaninglog.dto.AiReportApplyRequest;
import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.dto.LogNavigationResponse;
import com.chad.meaninglog.dto.MeaningLogRequest;
import com.chad.meaninglog.dto.MeaningLogResponse;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.MeaningLogService;
import com.chad.meaninglog.service.XiaojiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/logs")
public class MeaningLogController {

    private final MeaningLogService meaningLogService;
    private final XiaojiChatService xiaojiChatService;

    @GetMapping
    public List<MeaningLogResponse> findAll(
            @AuthenticationPrincipal UserAccount user,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(required = false)
            String keyword,
            @RequestParam(required = false)
            String tag,
            @RequestParam(required = false)
            Boolean favorite
    ) {
        return meaningLogService.findAll(user, date, keyword, tag, favorite);
    }

    @GetMapping("/{id}")
    public MeaningLogResponse findById(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return meaningLogService.findById(user, id);
    }

    @GetMapping("/images/{imageId}")
    public ResponseEntity<byte[]> findImage(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long imageId
    ) {
        LogImage image = meaningLogService.getLogImage(user, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(image.getData());
    }

    @GetMapping("/{id}/navigation")
    public LogNavigationResponse findNavigation(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return meaningLogService.findNavigation(user, id);
    }

    @GetMapping("/ai/tags")
    public List<String> findAiTags(
            @AuthenticationPrincipal UserAccount user
    ) {
        return meaningLogService.findAiTags(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeaningLogResponse create(
            @AuthenticationPrincipal UserAccount user,
            @Valid @RequestBody MeaningLogRequest request
    ) {
        return meaningLogService.create(user, request);
    }

    @PutMapping("/{id}")
    public MeaningLogResponse update(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody MeaningLogRequest request
    ) {
        return meaningLogService.update(user, id, request);
    }

    @PutMapping("/{id}/favorite")
    public MeaningLogResponse updateFavorite(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @RequestParam boolean favorite
    ) {
        return meaningLogService.updateFavorite(user, id, favorite);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        meaningLogService.delete(user, id);
    }

    @PostMapping("/{id}/ai")
    public MeaningLogResponse generateAiForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return meaningLogService.generateAiForLog(user, id);
    }

    @PostMapping("/{id}/ai/chat")
    public AiChatResponse previewAiForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody AiChatRequest request
    ) {
        return xiaojiChatService.chatWithLog(user, id, request.getMessage());
    }

    @GetMapping("/{id}/ai/chat")
    public AiChatResponse findLogAiChat(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return xiaojiChatService.findLogMessages(user, id);
    }

    @PostMapping("/{id}/ai/apply")
    public MeaningLogResponse applyAiForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody AiApplyRequest request
    ) {
        LogAiResult result = new LogAiResult(request.getTitle(), request.getSummary(), request.getTags());
        return meaningLogService.applyAiForLog(user, id, result);
    }

    @PostMapping("/ai/daily-summary")
    public AiReportResponse summarizeDay(
            @AuthenticationPrincipal UserAccount user,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return meaningLogService.summarizeDay(user, date);
    }

    @PostMapping("/ai/report")
    public AiReportResponse summarizePeriod(
            @AuthenticationPrincipal UserAccount user,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            @RequestParam(defaultValue = "AI 报告")
            String title
    ) {
        return meaningLogService.summarizePeriod(user, startDate, endDate, title);
    }

    @GetMapping("/ai/reports")
    public List<AiReportResponse> findReports(
            @AuthenticationPrincipal UserAccount user
    ) {
        return meaningLogService.findReports(user);
    }

    @GetMapping("/ai/reports/{reportId}")
    public AiReportResponse findReport(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long reportId
    ) {
        return meaningLogService.findReport(user, reportId);
    }

    @PostMapping("/ai/reports/{reportId}/apply")
    public AiReportResponse applyReport(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long reportId,
            @Valid @RequestBody AiReportApplyRequest request
    ) {
        return meaningLogService.applyReport(user, reportId, request);
    }

    @GetMapping("/ai/reports/{reportId}/chat")
    public AiChatResponse findReportChat(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long reportId
    ) {
        return xiaojiChatService.findReportMessages(user, reportId);
    }

    @PostMapping("/ai/reports/{reportId}/chat")
    public AiChatResponse chatWithReport(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long reportId,
            @Valid @RequestBody AiChatRequest request
    ) {
        return xiaojiChatService.chatWithReport(user, reportId, request.getMessage());
    }
}
