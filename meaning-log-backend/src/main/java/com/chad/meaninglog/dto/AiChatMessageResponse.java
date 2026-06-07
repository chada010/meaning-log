package com.chad.meaninglog.dto;

import com.chad.meaninglog.entity.AiChatMessage;

import java.time.LocalDateTime;

public record AiChatMessageResponse(
        Long id,
        String role,
        String content,
        LocalDateTime createdAt
) {
    public static AiChatMessageResponse from(AiChatMessage message) {
        return new AiChatMessageResponse(
                message.getId(),
                message.getRole().name().toLowerCase(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
