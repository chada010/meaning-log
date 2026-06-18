package com.chad.meaninglog.client;

import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.dto.LogAiResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OpenAiClient {

    private static final String JOURNAL_ASSISTANT_PROMPT = """
            你叫「小记」，是 Meaning Log 里的专属日志总结智能体。
            你的任务是帮助用户温柔地回看自己的生活，而不是评判、说教或夸张鼓励。

            你的表达风格：
            1. 使用简体中文。
            2. 语气温柔、可爱、轻快、有陪伴感。
            3. 可以有一点点活泼，但不要油腻，不要过度卖萌。
            4. 多肯定用户记录生活这件事本身。
            5. 总结要具体，必须基于日志内容，不要编造不存在的事实。
            6. 如果日志内容普通，也要帮用户看见其中细小但真实的意义。
            7. 不要使用 emoji，不要使用 Markdown。

            输出必须是合法 JSON，不要在 JSON 外添加任何解释。
            """;

    private static final String REPORT_ASSISTANT_PROMPT = """
            你叫「小记」，是 Meaning Log 里的专属日志报告智能体。
            你的任务是帮用户把一段时间的日志整理成温柔、清晰、可回顾的总结。

            你的表达风格：
            1. 使用简体中文。
            2. 语气温柔、可爱、活泼一点，但保持真诚和克制。
            3. 像一个认真陪伴用户复盘生活的朋友。
            4. 重点提炼主要事件、情绪变化、反复出现的主题，以及值得被珍惜的小进步。
            5. 不要编造日志中没有的事实，不要空泛鸡汤。
            6. 不要使用 emoji，不要使用 Markdown。

            输出必须是合法 JSON，不要在 JSON 外添加任何解释。
            """;

    private static final String COMPANION_ASSISTANT_PROMPT = """
            你叫「小记」，是 Meaning Log 里的陪伴式 AI 助手。
            你可以陪用户聊天、梳理感受、温柔地复盘生活，也可以在用户需要时帮他想清楚下一步。

            你的表达风格：
            1. 使用简体中文。
            2. 语气温柔、自然、有陪伴感，像认真听人说话的朋友。
            3. 不要说教，不要强行积极，不要空泛鸡汤。
            4. 可以适度提问，帮助用户继续表达，但不要一次问太多。
            5. 如果用户明显处在强烈痛苦或危险中，要先表达关心，并建议联系现实中的可信任的人或当地紧急支持。
            6. 不要使用 emoji，不要使用 Markdown。
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAiClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${ai.api-key:}") String apiKey,
            @Value("${ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${ai.model:qwen-plus}") String model
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public LogAiResult analyzeLog(String logDate, String title, String mood, String content) {
        return analyzeLog(logDate, title, mood, content, List.of());
    }

    public LogAiResult analyzeLog(String logDate, String title, String mood, String content, List<ImageInput> images) {
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
                """.formatted(logDate, title, blankToNone(mood), content);

        String contentText = createChatCompletion(List.of(
                Map.of("role", "system", "content", JOURNAL_ASSISTANT_PROMPT),
                Map.of("role", "user", "content", buildUserContent(userPrompt, images))
        ), 900, 0.7);
        return readJson(contentText, LogAiResult.class);
    }

    public LogAiResult refineLogSummary(
            String logDate,
            String title,
            String mood,
            String content,
            String currentAiTitle,
            String currentAiSummary,
            String currentAiTags,
            List<ChatTurn> history,
            List<ImageInput> images,
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
                logDate,
                title,
                blankToNone(mood),
                content,
                blankToNone(currentAiTitle),
                blankToNone(currentAiSummary),
                blankToNone(currentAiTags)
        );

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", JOURNAL_ASSISTANT_PROMPT));
        messages.add(Map.of("role", "user", "content", buildUserContent(contextPrompt, images)));
        messages.addAll(history.stream()
                .map(turn -> Map.<String, Object>of("role", turn.role(), "content", turn.content()))
                .toList());
        messages.add(Map.of("role", "user", "content", userMessage));

        String contentText = createChatCompletion(messages, 900, 0.7);
        return readJson(contentText, LogAiResult.class);
    }

    public String chatWithCompanion(List<ChatTurn> history, String userMessage) {
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", COMPANION_ASSISTANT_PROMPT));
        messages.addAll(history.stream()
                .map(turn -> Map.<String, Object>of("role", turn.role(), "content", turn.content()))
                .toList());
        messages.add(Map.of("role", "user", "content", userMessage));
        return createChatCompletion(messages, 900, 0.75);
    }

    public void streamChatWithCompanion(
            List<ChatTurn> history,
            String userMessage,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", COMPANION_ASSISTANT_PROMPT));
        messages.addAll(history.stream()
                .map(turn -> Map.<String, Object>of("role", turn.role(), "content", turn.content()))
                .toList());
        messages.add(Map.of("role", "user", "content", userMessage));
        streamChatCompletion(messages, 900, 0.75, onChunk, onComplete);
    }

    public AiReportResponse summarizeReport(String title, String period, String logsText) {
        String focus = reportFocus(title);
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
                - 如果日志不足，也要诚实说明“样本还少”，不要编造趋势。

                报告标题：%s
                时间范围：%s
                日志内容：
                %s
                """.formatted(focus, title, period, logsText);

        String contentText = createChatCompletion(REPORT_ASSISTANT_PROMPT, userPrompt, 1200);
        return readJson(contentText, AiReportResponse.class);
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

    public AiReportResponse refineReport(
            String title,
            String period,
            String summary,
            String tags,
            List<ChatTurn> history,
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
                """.formatted(title, period, summary, blankToNone(tags));

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", REPORT_ASSISTANT_PROMPT));
        messages.add(Map.of("role", "user", "content", contextPrompt));
        messages.addAll(history.stream()
                .map(turn -> Map.<String, Object>of("role", turn.role(), "content", turn.content()))
                .toList());
        messages.add(Map.of("role", "user", "content", userMessage));

        String contentText = createChatCompletion(messages, 1200, 0.7);
        return readJson(contentText, AiReportResponse.class);
    }

    public void streamChatCompletion(
            List<Map<String, Object>> messages,
            int maxTokens,
            double temperature,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        ensureConfigured();

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature,
                "max_tokens", maxTokens,
                "stream", true
        );

        try {
            restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(request)
                    .exchange((req, resp) -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring(5).trim();
                                if ("[DONE]".equals(data)) {
                                    onComplete.run();
                                    break;
                                }
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    JsonNode content = node.path("choices").path(0).path("delta").path("content");
                                    if (content.isTextual() && !content.asText().isEmpty()) {
                                        onChunk.accept(content.asText());
                                    }
                                } catch (Exception ignored) {
                                    // 跳过无法解析的 SSE 行
                                }
                            }
                        }
                        return null;
                    });
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI stream request failed: " + extractProviderError(ex),
                    ex
            );
        }
    }

    private String createChatCompletion(String systemPrompt, String userPrompt, int maxTokens) {
        return createChatCompletion(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ), maxTokens, 0.7);
    }

    private String createChatCompletion(List<Map<String, Object>> messages, int maxTokens, double temperature) {
        ensureConfigured();

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature,
                "max_tokens", maxTokens
        );

        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            return extractMessageContent(response);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI request failed: " + extractProviderError(ex),
                    ex
            );
        }
    }

    private void ensureConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI API key is not configured. Please set DASHSCOPE_API_KEY before starting the backend."
            );
        }
    }

    private String extractMessageContent(JsonNode response) {
        JsonNode content = response
                .path("choices")
                .path(0)
                .path("message")
                .path("content");

        if (content.isTextual() && !content.asText().isBlank()) {
            return stripMarkdownFence(content.asText());
        }

        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response did not contain message content");
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to parse AI JSON output: " + value, ex);
        }
    }

    private String extractProviderError(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            JsonNode message = body.path("error").path("message");
            if (message.isTextual()) {
                return message.asText();
            }

            JsonNode code = body.path("code");
            JsonNode msg = body.path("message");
            if (code.isTextual() || msg.isTextual()) {
                return code.asText() + " " + msg.asText();
            }
        } catch (Exception ignored) {
            // Fall through to raw response body.
        }

        return ex.getResponseBodyAsString();
    }

    private String stripMarkdownFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        return trimmed
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }

    public record ChatTurn(String role, String content) {
    }

    public record ImageInput(String caption, String dataUrl) {
    }

    private Object buildUserContent(String text, List<ImageInput> images) {
        if (images == null || images.isEmpty()) {
            return text;
        }

        List<Map<String, Object>> content = new java.util.ArrayList<>();
        content.add(Map.of("type", "text", "text", text));
        for (int index = 0; index < images.size(); index++) {
            ImageInput image = images.get(index);
            if (image == null || image.dataUrl() == null || image.dataUrl().isBlank()) {
                continue;
            }
            String caption = image.caption() == null || image.caption().isBlank()
                    ? "未填写说明"
                    : image.caption();
            content.add(Map.of("type", "text", "text", "第" + (index + 1) + "张图片说明：" + caption));
            content.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", image.dataUrl())
            ));
        }
        return content;
    }
}
