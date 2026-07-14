package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiApplyRequest;
import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.dto.MeaningLogResponse;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.service.MeaningLogService;
import com.chad.meaninglog.service.XiaojiChatService;
import com.chad.meaninglog.web.SseEmitterSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final AiService aiService;
    private final SseEmitterSupport sseEmitterSupport;

    @Operation(summary = "获取用户所有 AI 标签", description = "用于标签筛选下拉")
    @GetMapping("/ai/tags")
    public List<String> findAiTags(
            @AuthenticationPrincipal UserAccount user
    ) {
        return meaningLogService.findAiTags(user);
    }

    @Operation(summary = "生成日志 AI 分析（一次性返回）", description = "返回标题、总结、标签的草稿，不落库")
    @PostMapping("/{id}/ai")
    public MeaningLogResponse generateAiForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return meaningLogService.generateAiForLog(user, id);
    }

    @Operation(summary = "生成日志 AI 分析（SSE 流式）")
    @PostMapping(value = "/{id}/ai/stream", produces = "text/event-stream")
    public SseEmitter generateAiForLogStream(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        try (SseEmitterSupport.Submission submission = sseEmitterSupport.reserveSubmission()) {
            SseEmitter emitter = sseEmitterSupport.create(response);
            MeaningLogService.AnalyzeStreamContext ctx = meaningLogService.prepareAnalyzeStream(user, id);

            sseEmitterSupport.submit(submission, emitter, () -> aiService.streamAnalyzeLog(
                    ctx.log(),
                    ctx.images(),
                    chunk -> sseEmitterSupport.sendData(emitter, chunk),
                    () -> sseEmitterSupport.completeWithDone(emitter)
            ));

            return emitter;
        }
    }

    @Operation(summary = "与小记对话精修日志 AI 结果")
    @PostMapping("/{id}/ai/chat")
    public AiChatResponse previewAiForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody AiChatRequest request
    ) {
        return xiaojiChatService.chatWithLog(user, id, request.getMessage());
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

    @Operation(summary = "与小记对话精修日志 AI 结果（SSE 流式）")
    @PostMapping(value = "/{id}/ai/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStreamForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody AiChatRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        try (SseEmitterSupport.Submission submission = sseEmitterSupport.reserveSubmission()) {
            SseEmitter emitter = sseEmitterSupport.create(response);
            XiaojiChatService.LogRefineStreamContext ctx =
                    xiaojiChatService.prepareLogRefineStream(user, id, request.getMessage());

            StringBuilder buffer = new StringBuilder();

            sseEmitterSupport.submit(submission, emitter, () -> aiService.streamRefineLogSummary(
                    ctx.log(),
                    ctx.history(),
                    ctx.images(),
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
    }
}
