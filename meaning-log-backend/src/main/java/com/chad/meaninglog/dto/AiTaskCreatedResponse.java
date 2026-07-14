package com.chad.meaninglog.dto;

import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.entity.AiTaskType;

public record AiTaskCreatedResponse(
        Long taskId,
        AiTaskType taskType,
        AiTaskStatus status
) {
    public static AiTaskCreatedResponse from(AiTask task) {
        return new AiTaskCreatedResponse(task.getId(), task.getTaskType(), task.getStatus());
    }
}
