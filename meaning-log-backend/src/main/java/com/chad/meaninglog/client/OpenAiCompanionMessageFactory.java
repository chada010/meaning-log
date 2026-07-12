package com.chad.meaninglog.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OpenAiCompanionMessageFactory {

    private final OpenAiMessageSupport support;

    OpenAiCompanionMessageFactory(OpenAiMessageSupport support) {
        this.support = support;
    }

    List<Map<String, Object>> messages(List<OpenAiClient.ChatTurn> history, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", OpenAiPrompts.COMPANION_ASSISTANT));
        messages.addAll(support.historyMessages(history));
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }
}
