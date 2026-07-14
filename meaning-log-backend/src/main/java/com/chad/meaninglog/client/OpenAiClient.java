package com.chad.meaninglog.client;

import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.dto.LogAiResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OpenAiClient {

    private final OpenAiTransport transport;
    private final OpenAiResponseParser responseParser;
    private final OpenAiJournalMessageFactory journalMessages;
    private final OpenAiReportMessageFactory reportMessages;
    private final OpenAiCompanionMessageFactory companionMessages;

    public OpenAiClient(OpenAiTransport transport) {
        this.transport = transport;
        this.responseParser = transport.responseParser();
        OpenAiMessageSupport messageSupport = new OpenAiMessageSupport();
        this.journalMessages = new OpenAiJournalMessageFactory(messageSupport);
        this.reportMessages = new OpenAiReportMessageFactory(messageSupport);
        this.companionMessages = new OpenAiCompanionMessageFactory(messageSupport);
    }

    public LogAiResult analyzeLog(String logDate, String title, String mood, String content) {
        return analyzeLog(logDate, title, mood, content, List.of());
    }

    public LogAiResult analyzeLog(String logDate, String title, String mood, String content, List<ImageInput> images) {
        return readJson(transport.complete(journalMessages.analysisMessages(logDate, title, mood, content, images), 900, 0.7), LogAiResult.class);
    }

    public void streamAnalyzeLog(
            String logDate,
            String title,
            String mood,
            String content,
            List<ImageInput> images,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        streamChatCompletion(journalMessages.analysisMessages(logDate, title, mood, content, images), 900, 0.7, onChunk, onComplete);
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
        return readJson(transport.complete(
                journalMessages.refinementMessages(
                        logDate, title, mood, content, currentAiTitle, currentAiSummary, currentAiTags, history, images, userMessage
                ),
                900,
                0.7
        ), LogAiResult.class);
    }

    public void streamRefineLogSummary(
            String logDate,
            String title,
            String mood,
            String content,
            String currentAiTitle,
            String currentAiSummary,
            String currentAiTags,
            List<ChatTurn> history,
            List<ImageInput> images,
            String userMessage,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        streamChatCompletion(
                journalMessages.refinementMessages(
                        logDate, title, mood, content, currentAiTitle, currentAiSummary, currentAiTags, history, images, userMessage
                ),
                900,
                0.7,
                onChunk,
                onComplete
        );
    }

    public String chatWithCompanion(List<ChatTurn> history, String userMessage) {
        return transport.complete(companionMessages.messages(history, userMessage), 900, 0.75);
    }

    public void streamChatWithCompanion(
            List<ChatTurn> history,
            String userMessage,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        streamChatCompletion(companionMessages.messages(history, userMessage), 900, 0.75, onChunk, onComplete);
    }

    public AiReportResponse summarizeReport(String title, String period, String logsText) {
        return readJson(transport.complete(reportMessages.summaryMessages(title, period, logsText), 1200, 0.7), AiReportResponse.class);
    }

    public void streamSummarizeReport(
            String title,
            String period,
            String logsText,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        streamChatCompletion(reportMessages.streamSummaryMessages(title, period, logsText), 1200, 0.7, onChunk, onComplete);
    }

    public AiReportResponse refineReport(
            String title,
            String period,
            String summary,
            String tags,
            List<ChatTurn> history,
            String userMessage
    ) {
        return readJson(
                transport.complete(reportMessages.refinementMessages(title, period, summary, tags, history, userMessage), 1200, 0.7),
                AiReportResponse.class
        );
    }

    public void streamRefineReport(
            String title,
            String period,
            String summary,
            String tags,
            List<ChatTurn> history,
            String userMessage,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        streamChatCompletion(
                reportMessages.refinementMessages(title, period, summary, tags, history, userMessage),
                1200,
                0.7,
                onChunk,
                onComplete
        );
    }

    public void streamChatCompletion(
            List<Map<String, Object>> messages,
            int maxTokens,
            double temperature,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        transport.stream(messages, maxTokens, temperature, onChunk, onComplete);
    }

    public record ChatTurn(String role, String content) {
    }

    public record ImageInput(String caption, String dataUrl) {
    }

    private <T> T readJson(String value, Class<T> type) {
        return responseParser.readJson(value, type);
    }
}
