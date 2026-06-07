package com.chad.meaninglog.dto;

import java.util.List;

public record LogAiResult(
        String title,
        String summary,
        List<String> tags
) {
}
