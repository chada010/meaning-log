package com.chad.meaninglog.dto;

public record LogNavigationResponse(
        MeaningLogResponse previous,
        MeaningLogResponse next
) {
}
