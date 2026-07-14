package com.chad.meaninglog.dto;

import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.entity.AiTaskType;

import java.time.LocalDateTime;

public record AiTaskResponse(
        Long id,
        AiTaskType taskType,
        AiTaskStatus status,
        String resultJson,
        String errorMessage,
        Integer retryCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AiTaskResponse from(AiTask task) {
        return new AiTaskResponse(
                task.getId(),
                task.getTaskType(),
                task.getStatus(),
                task.getResultJson(),
                task.getErrorMessage(),
                task.getRetryCount(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
