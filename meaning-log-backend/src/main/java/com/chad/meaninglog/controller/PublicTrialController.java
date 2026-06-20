package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.dto.TrialAnalyzeRequest;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.service.AiRateLimiter;
import com.chad.meaninglog.service.AiService;
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
import java.util.concurrent.ExecutorService;

/**
 * 游客试用：未登录用户可写一条临时日志并体验一次 AI 整理。
 * 不落库，仅返回 AI 结果；按 IP 限流防滥用。
 */
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trial")
public class PublicTrialController {

    private final AiService aiService;
    private final AiRateLimiter aiRateLimiter;
    private final ExecutorService sseExecutorService;

    @PostMapping("/analyze")
    public LogAiResult analyze(
            @Valid @RequestBody TrialAnalyzeRequest request,
            HttpServletRequest httpRequest
    ) {
        aiRateLimiter.checkTrial(resolveClientIp(httpRequest));

        MeaningLog log = new MeaningLog();
        log.setTitle(request.getTitle());
        log.setContent(request.getContent());
        log.setLogDate(request.getLogDate());
        log.setMood(request.getMood());

        return aiService.analyzeLog(log);
    }

    @PostMapping(value = "/analyze/stream", produces = "text/event-stream")
    public SseEmitter analyzeStream(
            @Valid @RequestBody TrialAnalyzeRequest request,
            HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse
    ) {
        httpResponse.setHeader("X-Accel-Buffering", "no");
        httpResponse.setHeader("Cache-Control", "no-cache");
        aiRateLimiter.checkTrial(resolveClientIp(httpRequest));

        MeaningLog log = new MeaningLog();
        log.setTitle(request.getTitle());
        log.setContent(request.getContent());
        log.setLogDate(request.getLogDate());
        log.setMood(request.getMood());

        SseEmitter emitter = new SseEmitter(120_000L);

        sseExecutorService.submit(() -> {
            try {
                aiService.streamAnalyzeLog(log, List.of(),
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data(""));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        }
                );
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
