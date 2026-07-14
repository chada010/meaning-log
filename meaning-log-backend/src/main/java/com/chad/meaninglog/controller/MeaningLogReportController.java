package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiReportApplyRequest;
import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.dto.AiTaskCreatedResponse;
import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskType;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.mq.AiTaskInputs;
import com.chad.meaninglog.service.AiRateLimiter;
import com.chad.meaninglog.service.AiTaskService;
import com.chad.meaninglog.service.MeaningLogService;
import com.chad.meaninglog.service.XiaojiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.chad.meaninglog.web.WebConstants.LOCAL_FRONTEND_ORIGIN;

@Tag(name = "AI 报告", description = "日报、周期报告的生成、精修与落库")
@CrossOrigin(origins = LOCAL_FRONTEND_ORIGIN)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/logs")
public class MeaningLogReportController {

    private final MeaningLogService meaningLogService;
    private final XiaojiChatService xiaojiChatService;
    private final AiRateLimiter aiRateLimiter;
    private final AiTaskService aiTaskService;

    @Operation(summary = "生成单日 AI 总结", description = "date 不传则默认今天")
    @PostMapping("/ai/daily-summary")
    public ResponseEntity<AiTaskCreatedResponse> summarizeDay(
            @AuthenticationPrincipal UserAccount user,
            @RequestParam(required = false) String date
    ) {
        var parsed = meaningLogService.parseDailySummaryDate(date);
        aiRateLimiter.check(user);
        AiTask task = aiTaskService.create(user, AiTaskType.REPORT_GENERATE,
                new AiTaskInputs.ReportGenerateInput(
                        AiTaskInputs.ReportMode.DAILY,
                        parsed,
                        parsed,
                        "AI 当天总结"));
        return ResponseEntity.accepted().body(AiTaskCreatedResponse.from(task));
    }

    @Operation(summary = "生成周期 AI 报告", description = "传入 startDate 与 endDate 汇总时间段内日志")
    @PostMapping("/ai/report")
    public ResponseEntity<AiTaskCreatedResponse> summarizePeriod(
            @AuthenticationPrincipal UserAccount user,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "AI 报告") String title
    ) {
        MeaningLogService.ReportDateRange range = meaningLogService.parseReportDateRange(startDate, endDate);
        aiRateLimiter.check(user);
        AiTask task = aiTaskService.create(user, AiTaskType.REPORT_GENERATE,
                new AiTaskInputs.ReportGenerateInput(
                        AiTaskInputs.ReportMode.PERIOD,
                        range.startDate(),
                        range.endDate(),
                        title));
        return ResponseEntity.accepted().body(AiTaskCreatedResponse.from(task));
    }

    @Operation(summary = "获取所有 AI 报告列表")
    @GetMapping("/ai/reports")
    public List<AiReportResponse> findReports(
            @AuthenticationPrincipal UserAccount user
    ) {
        return meaningLogService.findReports(user);
    }

    @Operation(summary = "获取指定 AI 报告详情")
    @GetMapping("/ai/reports/{reportId}")
    public AiReportResponse findReport(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long reportId
    ) {
        return meaningLogService.findReport(user, reportId);
    }

    @Operation(summary = "将 AI 报告精修结果落库")
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
    public ResponseEntity<AiTaskCreatedResponse> chatWithReport(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long reportId,
            @Valid @RequestBody AiChatRequest request
    ) {
        aiRateLimiter.check(user);
        AiTask task = aiTaskService.create(user, AiTaskType.REPORT_REFINE,
                new AiTaskInputs.ReportRefineInput(reportId, request.getMessage()));
        return ResponseEntity.accepted().body(AiTaskCreatedResponse.from(task));
    }
}
