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

@CrossOrigin(origins = LOCAL_FRONTEND_ORIGIN)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/logs")
public class MeaningLogAiController {

    private final MeaningLogService meaningLogService;
    private final XiaojiChatService xiaojiChatService;
    private final AiService aiService;
    private final SseEmitterSupport sseEmitterSupport;

    @GetMapping("/ai/tags")
    public List<String> findAiTags(
            @AuthenticationPrincipal UserAccount user
    ) {
        return meaningLogService.findAiTags(user);
    }

    @PostMapping("/{id}/ai")
    public MeaningLogResponse generateAiForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return meaningLogService.generateAiForLog(user, id);
    }

    @PostMapping(value = "/{id}/ai/stream", produces = "text/event-stream")
    public SseEmitter generateAiForLogStream(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        SseEmitter emitter = sseEmitterSupport.create(response);
        MeaningLogService.AnalyzeStreamContext ctx = meaningLogService.prepareAnalyzeStream(user, id);

        sseEmitterSupport.submit(emitter, () -> aiService.streamAnalyzeLog(
                ctx.log(),
                ctx.images(),
                chunk -> sseEmitterSupport.sendData(emitter, chunk),
                () -> sseEmitterSupport.completeWithDone(emitter)
        ));

        return emitter;
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

    @PostMapping(value = "/{id}/ai/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStreamForLog(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody AiChatRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        SseEmitter emitter = sseEmitterSupport.create(response);
        XiaojiChatService.LogRefineStreamContext ctx =
                xiaojiChatService.prepareLogRefineStream(user, id, request.getMessage());

        StringBuilder buffer = new StringBuilder();

        sseEmitterSupport.submit(emitter, () -> aiService.streamRefineLogSummary(
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
