package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiApplyRequest;
import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiTaskCreatedResponse;
import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.dto.MeaningLogResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.chad.meaninglog.web.WebConstants.LOCAL_FRONTEND_ORIGIN;

@Tag(name = "日志 AI", description = "针对单条日志的 AI 分析、对话精修与结果落库")
@CrossOrigin(origins = LOCAL_FRONTEND_ORIGIN)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/logs")
public class MeaningLogAiController {

    private final MeaningLogService meaningLogService;
    private final XiaojiChatService xiaojiChatService;
    private final AiRateLimiter aiRateLimiter;
    private final AiTaskService aiTaskService;

    @Operation(summary = "获取用户所有 AI 标签", description = "用于标签筛选下拉")
    @GetMapping("/ai/tags")
    public List<String> findAiTags(
            @AuthenticationPrincipal UserAccount user
    ) {
        return meaningLogService.findAiTags(user);
    }

    @Operation(summary = "生成日志 AI 分析（202 异步入队）", description = "入队后立返 taskId，客户端通过 /ai/tasks/{id} 或 SSE 获取结果")
    @PostMapping("/{id}/ai")
    public ResponseEntity<AiTaskCreatedResponse> generateAiForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        aiRateLimiter.check(user);
        AiTask task = aiTaskService.create(user, AiTaskType.LOG_ANALYZE,
                new AiTaskInputs.LogAnalyzeInput(id));
        return ResponseEntity.accepted().body(AiTaskCreatedResponse.from(task));
    }

    @Operation(summary = "与小记对话精修日志 AI 结果")
    @PostMapping("/{id}/ai/chat")
    public ResponseEntity<AiTaskCreatedResponse> previewAiForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody AiChatRequest request
    ) {
        aiRateLimiter.check(user);
        AiTask task = aiTaskService.create(user, AiTaskType.LOG_REFINE,
                new AiTaskInputs.LogRefineInput(id, request.getMessage()));
        return ResponseEntity.accepted().body(AiTaskCreatedResponse.from(task));
    }

    @Operation(summary = "获取日志的 AI 对话历史")
    @GetMapping("/{id}/ai/chat")
    public AiChatResponse findLogAiChat(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return xiaojiChatService.findLogMessages(user, id);
    }

    @Operation(summary = "将 AI 结果落库到日志", description = "确认草稿后显式写入 ai_title/ai_summary/ai_tags 字段")
    @PostMapping("/{id}/ai/apply")
    public MeaningLogResponse applyAiForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody AiApplyRequest request
    ) {
        LogAiResult result = new LogAiResult(request.getTitle(), request.getSummary(), request.getTags());
        return meaningLogService.applyAiForLog(user, id, result);
    }
}
