package com.chad.meaninglog.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OpenAiMessageSupport {

    Object buildUserContent(String text, List<OpenAiClient.ImageInput> images) {
        if (images == null || images.isEmpty()) {
            return text;
        }

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", text));
        for (int index = 0; index < images.size(); index++) {
            OpenAiClient.ImageInput image = images.get(index);
            if (image == null || image.dataUrl() == null || image.dataUrl().isBlank()) {
                continue;
            }
            String caption = image.caption() == null || image.caption().isBlank() ? "未填写说明" : image.caption();
            content.add(Map.of("type", "text", "text", "第" + (index + 1) + "张图片说明：" + caption));
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", image.dataUrl())));
        }
        return content;
    }

    List<Map<String, Object>> historyMessages(List<OpenAiClient.ChatTurn> history) {
        return history.stream()
                .map(turn -> Map.<String, Object>of("role", turn.role(), "content", turn.content()))
                .toList();
    }

    String blankToNone(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }
}
