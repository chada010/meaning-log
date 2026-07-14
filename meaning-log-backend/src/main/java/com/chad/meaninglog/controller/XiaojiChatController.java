package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiChatMessageResponse;
import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.dto.AiChatSessionResponse;
import com.chad.meaninglog.dto.AiTaskCreatedResponse;
import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskType;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.mq.AiTaskInputs;
import com.chad.meaninglog.service.AiRateLimiter;
import com.chad.meaninglog.service.AiTaskService;
import com.chad.meaninglog.service.XiaojiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "小记陪伴聊天", description = "开放式陪伴对话（/xiaoji），会话与消息持久化")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/xiaoji")
public class XiaojiChatController {

    private final XiaojiChatService xiaojiChatService;
    private final AiRateLimiter aiRateLimiter;
    private final AiTaskService aiTaskService;

    @Operation(summary = "获取当前用户全部陪伴会话")
    @GetMapping("/sessions")
    public List<AiChatSessionResponse> findGeneralSessions(
            @AuthenticationPrincipal UserAccount user
    ) {
        return xiaojiChatService.findGeneralSessions(user);
    }

    @Operation(summary = "获取指定会话的消息列表")
    @GetMapping("/sessions/{sessionId}/messages")
    public List<AiChatMessageResponse> findMessages(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long sessionId
    ) {
        return xiaojiChatService.findMessages(user, sessionId);
    }

    @Operation(summary = "陪伴聊天（202 异步入队）", description = "入队后立返 taskId，客户端通过 /ai/tasks/{id} 或 SSE 获取结果")
    @PostMapping("/chat")
    public ResponseEntity<AiTaskCreatedResponse> chat(
            @AuthenticationPrincipal UserAccount user,
            @Valid @RequestBody AiChatRequest request
    ) {
        aiRateLimiter.check(user);
        AiTask task = aiTaskService.create(user, AiTaskType.CHAT,
                new AiTaskInputs.ChatInput(request.getSessionId(), request.getMessage()));
        return ResponseEntity.accepted().body(AiTaskCreatedResponse.from(task));
    }
}
