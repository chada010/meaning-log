package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;

import java.time.LocalDateTime;

public interface AiTaskRepository extends BaseMapper<AiTask> {

    default AiTask save(AiTask task) {
        if (task.getId() == null) {
            insert(task);
        } else {
            updateById(task);
        }
        return task;
    }

    default int transitionToRunning(Long id) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, AiTaskStatus.PENDING)
                .set(AiTask::getStatus, AiTaskStatus.RUNNING));
    }

    default int reapStaleRunning(LocalDateTime cutoff, String errorMessage) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getStatus, AiTaskStatus.RUNNING)
                .lt(AiTask::getUpdatedAt, cutoff)
                .set(AiTask::getStatus, AiTaskStatus.FAILED)
                .set(AiTask::getErrorMessage, errorMessage));
    }
}
