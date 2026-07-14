package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.dto.TrialAnalyzeRequest;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.service.AiRateLimiter;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.web.ClientIpResolver;
import com.chad.meaninglog.web.SseEmitterSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static com.chad.meaninglog.web.WebConstants.LOCAL_FRONTEND_ORIGIN;

/**
 * 游客试用：未登录用户可写一条临时日志并体验一次 AI 整理。
 * 不落库，仅返回 AI 结果；按 IP 限流防止滥用。
 */
@Tag(name = "游客试用", description = "未登录用户体验 AI 整理，按 IP 限流，不落库")
@CrossOrigin(origins = LOCAL_FRONTEND_ORIGIN)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trial")
public class PublicTrialController {

    private final AiService aiService;
    private final AiRateLimiter aiRateLimiter;
    private final SseEmitterSupport sseEmitterSupport;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "游客提交日志换取一次 AI 分析", description = "不落库，仅返回结果")
    @PostMapping("/analyze")
    public LogAiResult analyze(
            @Valid @RequestBody TrialAnalyzeRequest request,
            HttpServletRequest httpRequest
    ) {
        aiRateLimiter.checkTrial(clientIpResolver.resolve(httpRequest));

        MeaningLog log = new MeaningLog();
        log.setTitle(request.getTitle());
        log.setContent(request.getContent());
        log.setLogDate(request.getLogDate());
        log.setMood(request.getMood());

        return aiService.analyzeLog(log);
    }

    @Operation(summary = "游客体验 AI 分析（SSE 流式）")
    @PostMapping(value = "/analyze/stream", produces = "text/event-stream")
    public SseEmitter analyzeStream(
            @Valid @RequestBody TrialAnalyzeRequest request,
            HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse
    ) {
        try (SseEmitterSupport.Submission submission = sseEmitterSupport.reserveSubmission()) {
            aiRateLimiter.checkTrial(clientIpResolver.resolve(httpRequest));

            MeaningLog log = new MeaningLog();
            log.setTitle(request.getTitle());
            log.setContent(request.getContent());
            log.setLogDate(request.getLogDate());
            log.setMood(request.getMood());

            SseEmitter emitter = sseEmitterSupport.create(httpResponse);

            sseEmitterSupport.submit(submission, emitter, () -> aiService.streamAnalyzeLog(
                    log,
                    List.of(),
                    chunk -> sseEmitterSupport.sendData(emitter, chunk),
                    () -> sseEmitterSupport.completeWithDone(emitter)
            ));

            return emitter;
        }
    }
}
