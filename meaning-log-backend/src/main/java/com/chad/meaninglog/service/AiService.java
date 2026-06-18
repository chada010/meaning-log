package com.chad.meaninglog.service;

import com.chad.meaninglog.client.OpenAiClient;
import com.chad.meaninglog.dto.AiReportResponse;
import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.MeaningLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiService {

    private final OpenAiClient openAiClient;

    public LogAiResult analyzeLog(MeaningLog log) {
        return analyzeLog(log, List.of());
    }

    public LogAiResult analyzeLog(MeaningLog log, List<LogImage> images) {
        return openAiClient.analyzeLog(
                log.getLogDate().toString(),
                log.getTitle(),
                log.getMood(),
                log.getContent(),
                toImageInputs(images)
        );
    }

    public LogAiResult refineLogSummary(MeaningLog log, String message) {
        return refineLogSummary(log, List.of(), message);
    }

    public LogAiResult refineLogSummary(MeaningLog log, List<OpenAiClient.ChatTurn> history, String message) {
        return refineLogSummary(log, history, List.of(), message);
    }

    public LogAiResult refineLogSummary(
            MeaningLog log,
            List<OpenAiClient.ChatTurn> history,
            List<LogImage> images,
            String message
    ) {
        return openAiClient.refineLogSummary(
                log.getLogDate().toString(),
                log.getTitle(),
                log.getMood(),
                log.getContent(),
                log.getAiTitle(),
                log.getAiSummary(),
                log.getAiTags(),
                history,
                toImageInputs(images),
                message
        );
    }

    public String chatWithCompanion(List<OpenAiClient.ChatTurn> history, String message) {
        return openAiClient.chatWithCompanion(history, message);
    }

    public void streamChatWithCompanion(
            List<OpenAiClient.ChatTurn> history,
            String message,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        openAiClient.streamChatWithCompanion(history, message, onChunk, onComplete);
    }

    public AiReportResponse summarizeLogs(String title, String period, List<MeaningLog> logs) {
        String logsText = logs.isEmpty()
                ? "这段时间没有日志记录。"
                : logs.stream()
                .map(this::formatLog)
                .collect(Collectors.joining("\n\n"));

        return openAiClient.summarizeReport(title, period, logsText);
    }

    public AiReportResponse refineReport(
            String title,
            String period,
            String summary,
            String tags,
            List<OpenAiClient.ChatTurn> history,
            String message
    ) {
        return openAiClient.refineReport(title, period, summary, tags, history, message);
    }

    private String formatLog(MeaningLog log) {
        return """
                日期：%s
                标题：%s
                心情：%s
                内容：%s
                """.formatted(
                log.getLogDate(),
                log.getTitle(),
                log.getMood() == null || log.getMood().isBlank() ? "未填写" : log.getMood(),
                log.getContent()
        );
    }

    private List<OpenAiClient.ImageInput> toImageInputs(List<LogImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        return images.stream()
                .map(image -> {
                    String base64 = java.util.Base64.getEncoder().encodeToString(image.getData());
                    return new OpenAiClient.ImageInput(
                            image.getCaption(),
                            "data:" + image.getContentType() + ";base64," + base64
                    );
                })
                .toList();
    }
}
