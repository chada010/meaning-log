package com.chad.meaninglog.mq;

import com.chad.meaninglog.entity.AiTaskType;

public record AiTaskMessage(Long taskId, AiTaskType taskType) {
}
