package com.chad.meaninglog.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class OpenAiReportMessageFactory {

    private final OpenAiMessageSupport support;

    OpenAiReportMessageFactory(OpenAiMessageSupport support) {
        this.support = support;
    }

    List<Map<String, Object>> summaryMessages(String title, String period, String logsText) {
        return summaryMessages(title, period, logsText, "“样本还少”");
    }

    List<Map<String, Object>> streamSummaryMessages(String title, String period, String logsText) {
        return summaryMessages(title, period, logsText, "\"样本还少\"");
    }

    private List<Map<String, Object>> summaryMessages(String title, String period, String logsText, String sampleText) {
        String userPrompt = """
                请根据下面一组日志生成一份温柔、清晰、像朋友陪伴复盘一样的中文报告。

                输出 JSON 格式必须是：
                {"title":"报告标题","period":"时间范围","summary":"报告正文","tags":"标签1,标签2,标签3"}

                字段要求：
                - title：保留或优化为适合展示的报告标题。
                - period：使用给定时间范围。
                - summary：220 到 650 个汉字，分成自然段也可以，但仍然必须放在 JSON 字符串里。
                - tags：用英文逗号分隔，3 到 8 个中文标签。
                - 重点方向：%s
                - 必须从多篇日志里提炼重复出现的感受、事件类型、在意点或生活节奏。
                - 可以温柔地提出一个很小、可执行的观察或提醒，但不要说教。
                - 如果日志不足，也要诚实说明%s，不要编造趋势。

                报告标题：%s
                时间范围：%s
                日志内容：
                %s
                """.formatted(reportFocus(title), sampleText, title, period, logsText);
        return List.of(
                Map.of("role", "system", "content", OpenAiPrompts.REPORT_ASSISTANT),
                Map.of("role", "user", "content", userPrompt)
        );
    }

    List<Map<String, Object>> refinementMessages(
            String title, String period, String summary, String tags, List<OpenAiClient.ChatTurn> history,
            String userMessage
    ) {
        String contextPrompt = """
                用户正在和你对话，希望你基于当前 AI 报告内容，按他的要求改善报告。
                请把用户的偏好落实到新的报告标题、正文和标签里，而不是只回答建议。

                输出 JSON 格式必须是：
                {"title":"报告标题","period":"时间范围","summary":"报告正文","tags":"标签1,标签2,标签3"}

                字段要求：
                - title：保留或优化为适合展示的报告标题。
                - period：保留当前时间范围。
                - summary：180 到 650 个汉字，允许分成自然段，但必须放在 JSON 字符串里。
                - tags：用英文逗号分隔，3 到 8 个中文标签。
                - 不要编造当前报告没有依据的新事实。
                - 不要使用 emoji，不要使用 Markdown。

                当前报告标题：%s
                当前时间范围：%s
                当前报告正文：
                %s
                当前标签：%s
                """.formatted(title, period, summary, support.blankToNone(tags));
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", OpenAiPrompts.REPORT_ASSISTANT));
        messages.add(Map.of("role", "user", "content", contextPrompt));
        messages.addAll(support.historyMessages(history));
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    private String reportFocus(String title) {
        String value = title == null ? "" : title;
        if (value.contains("情绪")) {
            return "最近情绪趋势。请观察心情词、压力来源、恢复方式和情绪起伏，帮用户看见最近自己是怎样被生活影响、又怎样照顾自己的。";
        }
        if (value.contains("在意") || value.contains("反复") || value.contains("主题")) {
            return "反复在意的事情。请从多篇日志中提取用户最近频繁惦记、反复提到、持续消耗或持续珍惜的主题。";
        }
        if (value.contains("周")) {
            return "每周总结。请按这一周的主要事件、情绪变化、反复主题、值得保留的小进展来整理。";
        }
        if (value.contains("月")) {
            return "月度回顾。请观察这个月的节奏、关系、任务、情绪和变化。";
        }
        return "综合陪伴式复盘。请帮用户温柔地看清这段时间发生了什么、自己在意什么、哪些细小变化值得被记住。";
    }
}
