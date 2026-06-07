package com.chad.meaninglog.dto;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.LogImage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MeaningLogResponse {

    private Long id;
    private String title;
    private String content;
    private LocalDate logDate;
    private String mood;
    private String aiTitle;
    private String aiSummary;
    private String aiTags;
    private boolean favorite;
    private List<LogImageResponse> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MeaningLogResponse from(MeaningLog meaningLog) {
        return MeaningLogResponse.builder()
                .id(meaningLog.getId())
                .title(meaningLog.getTitle())
                .content(meaningLog.getContent())
                .logDate(meaningLog.getLogDate())
                .mood(meaningLog.getMood())
                .aiTitle(meaningLog.getAiTitle())
                .aiSummary(meaningLog.getAiSummary())
                .aiTags(meaningLog.getAiTags())
                .favorite(meaningLog.isFavorite())
                .images(List.of())
                .createdAt(meaningLog.getCreatedAt())
                .updatedAt(meaningLog.getUpdatedAt())
                .build();
    }

    public static MeaningLogResponse from(MeaningLog meaningLog, List<LogImage> images) {
        MeaningLogResponse response = from(meaningLog);
        response.images = images.stream().map(LogImageResponse::from).toList();
        return response;
    }
}
