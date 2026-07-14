package com.chad.meaninglog.mq;

import java.time.LocalDate;

public final class AiTaskInputs {

    public enum ReportMode {
        DAILY,
        PERIOD
    }

    public record LogAnalyzeInput(Long logId) {
    }

    public record LogRefineInput(Long logId, String message) {
    }

    public record ReportGenerateInput(
            ReportMode mode,
            LocalDate startDate,
            LocalDate endDate,
            String title
    ) {
    }

    public record ReportRefineInput(Long reportId, String message) {
    }

    public record ChatInput(Long sessionId, String message) {
    }

    private AiTaskInputs() {
    }
}
