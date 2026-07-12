package com.chad.meaninglog.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OpenAiJournalMessageFactory {

    private final OpenAiMessageSupport support;

    OpenAiJournalMessageFactory(OpenAiMessageSupport support) {
        this.support = support;
    }

    List<Map<String, Object>> analysisMessages(
            String logDate, String title, String mood, String content, List<OpenAiClient.ImageInput> images
    ) {
        String userPrompt = """
                请分析下面这条日志和随日志上传的图片，为它生成更适合回顾的 AI 标题、温柔总结和标签。

                输出 JSON 格式必须是：
                {"title":"标题","summary":"总结","tags":["标签1","标签2"]}

                字段要求：
                - title：不超过 30 个汉字，温柔、具体、有画面感。
                - summary：80 到 180 个汉字，像小记在轻轻帮用户回看今天。
                - tags：2 到 6 个简短中文标签，每个不超过 8 个汉字。
                - 如果有图片，请结合图片中能确定的内容，比如美景、作品、场景或完成成果；不能确定的不要猜。

                日志日期：%s
                用户原始标题：%s
                用户心情：%s
                日志内容：
                %s
                """.formatted(logDate, title, support.blankToNone(mood), content);
        return List.of(
                Map.of("role", "system", "content", OpenAiPrompts.JOURNAL_ASSISTANT),
                Map.of("role", "user", "content", support.buildUserContent(userPrompt, images))
        );
    }

    List<Map<String, Object>> refinementMessages(
            String logDate, String title, String mood, String content, String currentAiTitle, String currentAiSummary,
            String currentAiTags, List<OpenAiClient.ChatTurn> history, List<OpenAiClient.ImageInput> images,
            String userMessage
    ) {
        String contextPrompt = """
                用户正在和你对话，希望你基于原始日志和当前 AI 整理结果，按他的要求改善总结内容。
                请把用户的偏好落实到新的 AI 标题、总结和标签里，而不是只回答建议。

                输出 JSON 格式必须是：
                {"title":"标题","summary":"总结","tags":["标签1","标签2"]}

                字段要求：
                - title：不超过 30 个汉字，温柔、具体、有画面感。
                - summary：80 到 220 个汉字，允许比原总结更贴合用户要求。
                - tags：2 到 6 个简短中文标签，每个不超过 8 个汉字。
                - 必须基于日志事实和图片中能确定的内容，不要编造新事件或看不清的细节。
                - 不要使用 emoji，不要使用 Markdown。

                日志日期：%s
                用户原始标题：%s
                用户心情：%s
                日志内容：
                %s

                当前 AI 标题：%s
                当前 AI 总结：%s
                当前 AI 标签：%s

                """.formatted(
                logDate, title, support.blankToNone(mood), content, support.blankToNone(currentAiTitle),
                support.blankToNone(currentAiSummary), support.blankToNone(currentAiTags)
        );
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", OpenAiPrompts.JOURNAL_ASSISTANT));
        messages.add(Map.of("role", "user", "content", support.buildUserContent(contextPrompt, images)));
        messages.addAll(support.historyMessages(history));
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }
}
