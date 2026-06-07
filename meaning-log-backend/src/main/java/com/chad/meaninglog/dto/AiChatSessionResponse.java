package com.chad.meaninglog.dto;

import com.chad.meaninglog.entity.AiChatSession;

import java.time.LocalDateTime;

public record AiChatSessionResponse(
        Long id,
        String type,
        Long logId,
        Long reportId,
        String title,
        LocalDateTime updatedAt
) {
    public static AiChatSessionResponse from(AiChatSession session) {
        return new AiChatSessionResponse(
                session.getId(),
                session.getType().name().toLowerCase(),
                session.getMeaningLogId(),
                session.getAiReportId(),
                session.getTitle(),
                session.getUpdatedAt()
        );
    }
}
