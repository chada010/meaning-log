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

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
