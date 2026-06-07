package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiChatMessageResponse;
import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.dto.AiChatResponse;
import com.chad.meaninglog.dto.AiChatSessionResponse;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.XiaojiChatService;
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

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/xiaoji")
public class XiaojiChatController {

    private final XiaojiChatService xiaojiChatService;

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
}
