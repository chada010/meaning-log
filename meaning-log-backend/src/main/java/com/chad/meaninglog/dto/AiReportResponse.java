package com.chad.meaninglog.dto;

import com.chad.meaninglog.entity.AiReport;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class AiReportResponse {

    private Long id;
    private String type;
    private String title;
    private String period;
    private LocalDate startDate;
    private LocalDate endDate;
    private String summary;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AiReportResponse from(AiReport report) {
        return AiReportResponse.builder()
                .id(report.getId())
                .type(report.getType().name().toLowerCase())
                .title(report.getTitle())
                .period(report.getPeriod())
                .startDate(report.getStartDate())
                .endDate(report.getEndDate())
                .summary(report.getSummary())
                .tags(report.getTags())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
