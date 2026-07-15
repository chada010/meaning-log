package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface AiTaskRepository extends BaseMapper<AiTask> {

    default AiTask save(AiTask task) {
        if (task.getId() == null) {
            insert(task);
        } else {
            updateById(task);
        }
        return task;
    }

    default List<AiTask> findPendingForPublish(LocalDateTime cutoff, int batchSize) {
        return selectList(new LambdaQueryWrapper<AiTask>()
                .select(AiTask::getId, AiTask::getTaskType)
                .eq(AiTask::getStatus, AiTaskStatus.PENDING)
                .lt(AiTask::getCreatedAt, cutoff)
                .and(query -> query
                        .isNull(AiTask::getLastPublishAt)
                        .or()
                        .lt(AiTask::getLastPublishAt, cutoff))
                .orderByAsc(AiTask::getLastPublishAt, AiTask::getCreatedAt, AiTask::getId)
                .last("LIMIT " + validatedBatchSize(batchSize)));
    }

    default int recordPublishAttempt(Long id, LocalDateTime attemptedAt) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, AiTaskStatus.PENDING)
                .set(AiTask::getLastPublishAt, attemptedAt)
                .set(AiTask::getUpdatedAt, attemptedAt)
                .setSql("publish_attempts = publish_attempts + 1"));
    }

    default int recordDuePublishAttempt(Long id, LocalDateTime cutoff, LocalDateTime attemptedAt) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, AiTaskStatus.PENDING)
                .lt(AiTask::getCreatedAt, cutoff)
                .and(query -> query
                        .isNull(AiTask::getLastPublishAt)
                        .or()
                        .lt(AiTask::getLastPublishAt, cutoff))
                .set(AiTask::getLastPublishAt, attemptedAt)
                .set(AiTask::getUpdatedAt, attemptedAt)
                .setSql("publish_attempts = publish_attempts + 1"));
    }

    default int transitionToRunning(Long id, LocalDateTime startedAt) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, AiTaskStatus.PENDING)
                .set(AiTask::getStatus, AiTaskStatus.RUNNING)
                .set(AiTask::getUpdatedAt, startedAt));
    }

    default int completeRunning(Long id, String resultJson, LocalDateTime completedAt) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, AiTaskStatus.RUNNING)
                .set(AiTask::getStatus, AiTaskStatus.SUCCESS)
                .set(AiTask::getResultJson, resultJson)
                .set(AiTask::getErrorMessage, null)
                .set(AiTask::getUpdatedAt, completedAt));
    }

    default int returnRunningToPending(Long id, String errorMessage, LocalDateTime failedAt) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, AiTaskStatus.RUNNING)
                .set(AiTask::getStatus, AiTaskStatus.PENDING)
                .set(AiTask::getErrorMessage, errorMessage)
                .set(AiTask::getUpdatedAt, failedAt)
                .setSql("retry_count = retry_count + 1"));
    }

    default int failRunning(Long id, String errorMessage, LocalDateTime failedAt) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, AiTaskStatus.RUNNING)
                .set(AiTask::getStatus, AiTaskStatus.FAILED)
                .set(AiTask::getErrorMessage, errorMessage)
                .set(AiTask::getUpdatedAt, failedAt)
                .setSql("retry_count = retry_count + 1"));
    }

    default int failPendingFromDlq(Long id, String errorMessage, LocalDateTime failedAt) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, AiTaskStatus.PENDING)
                .set(AiTask::getStatus, AiTaskStatus.FAILED)
                .set(AiTask::getErrorMessage, errorMessage)
                .set(AiTask::getUpdatedAt, failedAt));
    }

    default List<AiTask> findStaleRunning(LocalDateTime cutoff, int batchSize) {
        return selectList(new LambdaQueryWrapper<AiTask>()
                .select(AiTask::getId)
                .eq(AiTask::getStatus, AiTaskStatus.RUNNING)
                .lt(AiTask::getUpdatedAt, cutoff)
                .orderByAsc(AiTask::getUpdatedAt, AiTask::getId)
                .last("LIMIT " + validatedBatchSize(batchSize)));
    }

    default int failStaleRunning(
            Long id,
            LocalDateTime cutoff,
            String errorMessage,
            LocalDateTime failedAt
    ) {
        return update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, id)
                .eq(AiTask::getStatus, AiTaskStatus.RUNNING)
                .lt(AiTask::getUpdatedAt, cutoff)
                .set(AiTask::getStatus, AiTaskStatus.FAILED)
                .set(AiTask::getErrorMessage, errorMessage)
                .set(AiTask::getUpdatedAt, failedAt));
    }

    private static int validatedBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("AI task batch size must be between 1 and 500");
        }
        return batchSize;
    }
}
