package com.chad.meaninglog.dto;

import java.util.List;

public record AiChatResponse(
        Long sessionId,
        String reply,
        LogAiResult suggestion,
        AiReportResponse reportSuggestion,
        List<AiChatMessageResponse> messages
) {
}
