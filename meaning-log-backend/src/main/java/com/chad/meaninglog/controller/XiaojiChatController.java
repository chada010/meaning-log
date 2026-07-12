package com.chad.meaninglog.controller;

import com.chad.meaninglog.client.OpenAiClient;
import com.chad.meaninglog.dto.AiChatMessageResponse;
import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiChatSessionResponse;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.service.XiaojiChatService;
import com.chad.meaninglog.web.SseEmitterSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static com.chad.meaninglog.web.WebConstants.SSE_DONE_EVENT;
import static com.chad.meaninglog.web.WebConstants.SSE_SESSION_EVENT;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/xiaoji")
public class XiaojiChatController {

    private final XiaojiChatService xiaojiChatService;
    private final AiService aiService;
    private final SseEmitterSupport sseEmitterSupport;

    @GetMapping("/sessions")
    public List<AiChatSessionResponse> findGeneralSessions(
            @AuthenticationPrincipal UserAccount user
    ) {
        return xiaojiChatService.findGeneralSessions(user);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<AiChatMessageResponse> findMessages(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long sessionId
    ) {
        return xiaojiChatService.findMessages(user, sessionId);
    }

    @PostMapping("/chat")
    public AiChatResponse chat(
            @AuthenticationPrincipal UserAccount user,
            @Valid @RequestBody AiChatRequest request
    ) {
        return xiaojiChatService.chatWithCompanion(user, request.getSessionId(), request.getMessage());
    }

    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public SseEmitter chatStream(
            @AuthenticationPrincipal UserAccount user,
            @Valid @RequestBody AiChatRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        try (SseEmitterSupport.Submission submission = sseEmitterSupport.reserveSubmission()) {
            SseEmitter emitter = sseEmitterSupport.create(response);
            AiChatSession session = xiaojiChatService.prepareCompanionStream(
                    user, request.getSessionId(), request.getMessage());
            List<OpenAiClient.ChatTurn> history = xiaojiChatService.buildCompanionHistory(session);

            StringBuilder buffer = new StringBuilder();

            sseEmitterSupport.submit(submission, emitter, () -> {
                sseEmitterSupport.sendEvent(emitter, SSE_SESSION_EVENT, Map.of("sessionId", session.getId()));
                aiService.streamChatWithCompanion(history, request.getMessage(), chunk -> {
                    buffer.append(chunk);
                    sseEmitterSupport.sendData(emitter, chunk);
                }, () -> {
                    xiaojiChatService.persistStreamReply(session, buffer.toString());
                    sseEmitterSupport.completeWithDone(emitter);
                });
            });

            return emitter;
        }
    }
}
