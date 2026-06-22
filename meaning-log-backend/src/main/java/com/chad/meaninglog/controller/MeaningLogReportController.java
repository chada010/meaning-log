package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiReportApplyRequest;
import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.service.MeaningLogService;
import com.chad.meaninglog.service.XiaojiChatService;
import com.chad.meaninglog.web.SseEmitterSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;

import static com.chad.meaninglog.web.WebConstants.LOCAL_FRONTEND_ORIGIN;
import static com.chad.meaninglog.web.WebConstants.SSE_DONE_EVENT;

@CrossOrigin(origins = LOCAL_FRONTEND_ORIGIN)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/logs")
public class MeaningLogReportController {

    private final MeaningLogService meaningLogService;
    private final XiaojiChatService xiaojiChatService;
    private final AiService aiService;
    private final SseEmitterSupport sseEmitterSupport;
    private final ObjectMapper objectMapper;

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

    @PostMapping(value = "/ai/reports/{reportId}/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStreamForReport(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long reportId,
            @Valid @RequestBody AiChatRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        SseEmitter emitter = sseEmitterSupport.create(response);
        XiaojiChatService.ReportRefineStreamContext ctx =
                xiaojiChatService.prepareReportRefineStream(user, reportId, request.getMessage());

        StringBuilder buffer = new StringBuilder();

        sseEmitterSupport.submit(emitter, () -> aiService.streamRefineReport(
                ctx.report().getTitle(),
                ctx.report().getPeriod(),
                ctx.report().getSummary(),
                ctx.report().getTags(),
                ctx.history(),
                request.getMessage(),
                chunk -> {
                    buffer.append(chunk);
                    sseEmitterSupport.sendData(emitter, chunk);
                },
                () -> {
                    xiaojiChatService.persistStreamReply(ctx.session(), buffer.toString());
                    sseEmitterSupport.completeWithDone(emitter);
                }
        ));

        return emitter;
    }

    record ReportStreamRequest(LocalDate startDate, LocalDate endDate, String title) {
        String resolvedTitle() {
            return title == null || title.isBlank() ? "AI 报告" : title;
        }
    }

    record DailySummaryStreamRequest(LocalDate date) {
    }

    @PostMapping(value = "/ai/report/stream", produces = "text/event-stream")
    public SseEmitter generateReportStream(
            @AuthenticationPrincipal UserAccount user,
            @RequestBody ReportStreamRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        MeaningLogService.ReportStreamContext ctx = meaningLogService.prepareReportStream(
                user, request.startDate(), request.endDate(), request.resolvedTitle());

        SseEmitter emitter = sseEmitterSupport.create(response);
        StringBuilder buffer = new StringBuilder();

        sseEmitterSupport.submit(emitter, () -> aiService.streamSummarizeLogs(
                request.resolvedTitle(),
                ctx.period(),
                ctx.logs(),
                chunk -> {
                    buffer.append(chunk);
                    sseEmitterSupport.sendData(emitter, chunk);
                },
                () -> {
                    try {
                        AiReportResponse aiResponse = objectMapper.readValue(
                                buffer.toString(), AiReportResponse.class);
                        AiReportResponse saved = meaningLogService.saveReport(
                                user, ctx.type(), ctx.startDate(), ctx.endDate(), aiResponse);
                        sseEmitterSupport.completeWithEvent(
                                emitter,
                                SSE_DONE_EVENT,
                                objectMapper.writeValueAsString(saved)
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        ));

        return emitter;
    }

    @PostMapping(value = "/ai/daily-summary/stream", produces = "text/event-stream")
    public SseEmitter generateDailySummaryStream(
            @AuthenticationPrincipal UserAccount user,
            @RequestBody DailySummaryStreamRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        MeaningLogService.ReportStreamContext ctx = meaningLogService.prepareDailySummaryStream(
                user, request.date());

        SseEmitter emitter = sseEmitterSupport.create(response);
        StringBuilder buffer = new StringBuilder();

        sseEmitterSupport.submit(emitter, () -> aiService.streamSummarizeLogs(
                "AI 当天总结",
                ctx.period(),
                ctx.logs(),
                chunk -> {
                    buffer.append(chunk);
                    sseEmitterSupport.sendData(emitter, chunk);
                },
                () -> {
                    try {
                        AiReportResponse aiResponse = objectMapper.readValue(
                                buffer.toString(), AiReportResponse.class);
                        AiReportResponse saved = meaningLogService.saveReport(
                                user, ctx.type(), ctx.startDate(), ctx.endDate(), aiResponse);
                        sseEmitterSupport.completeWithEvent(
                                emitter,
                                SSE_DONE_EVENT,
                                objectMapper.writeValueAsString(saved)
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        ));

        return emitter;
    }
}
