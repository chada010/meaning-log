package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiChatMessageResponse;
import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiChatSessionResponse;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.service.XiaojiChatService;
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
import java.util.concurrent.ExecutorService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/xiaoji")
public class XiaojiChatController {

    private final XiaojiChatService xiaojiChatService;
    private final AiService aiService;
    private final ExecutorService sseExecutorService;

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
            @Valid @RequestBody AiChatRequest request
    ) {
        SseEmitter emitter = new SseEmitter(120_000L);
        AiChatSession session = xiaojiChatService.prepareCompanionStream(
                user, request.getSessionId(), request.getMessage());
        List<OpenAiClient.ChatTurn> history = xiaojiChatService.buildCompanionHistory(session);

        StringBuilder buffer = new StringBuilder();

        sseExecutorService.submit(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("session")
                        .data(Map.of("sessionId", session.getId())));

                aiService.streamChatWithCompanion(history, request.getMessage(), chunk -> {
                    try {
                        buffer.append(chunk);
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, () -> {
                    try {
                        xiaojiChatService.persistStreamReply(session, buffer.toString());
                        emitter.send(SseEmitter.event().name("done").data(""));
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
