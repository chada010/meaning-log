package com.chad.meaninglog.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenAiRequestFactory {

    private final String model;

    OpenAiRequestFactory(String model) {
        this.model = model;
    }

    Map<String, Object> create(List<Map<String, Object>> messages, int maxTokens, double temperature, boolean streaming) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", temperature);
        request.put("max_tokens", maxTokens);
        if (streaming) {
            request.put("stream", true);
        }
        return request;
    }
}
