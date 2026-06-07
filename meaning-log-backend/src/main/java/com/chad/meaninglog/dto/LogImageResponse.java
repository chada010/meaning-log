package com.chad.meaninglog.dto;

import com.chad.meaninglog.entity.LogImage;

public record LogImageResponse(
        Long id,
        String fileName,
        String caption,
        String contentType,
        long fileSize,
        String url,
        String dataUrl
) {
    public static LogImageResponse from(LogImage image) {
        String base64 = java.util.Base64.getEncoder().encodeToString(image.getData());
        return new LogImageResponse(
                image.getId(),
                image.getFileName(),
                image.getCaption(),
                image.getContentType(),
                image.getFileSize(),
                "/logs/images/" + image.getId(),
                "data:" + image.getContentType() + ";base64," + base64
        );
    }
}
