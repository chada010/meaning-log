package com.chad.meaninglog.mq.listener;

import com.chad.meaninglog.client.AiUnavailableException;
import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.mq.AiTaskMessage;
import com.chad.meaninglog.repository.AiTaskRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.service.AiTaskNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiTaskExecutor {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final AiTaskRepository aiTaskRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;
    private final AiTaskNotifier aiTaskNotifier;

    @FunctionalInterface
    public interface Work<I> {
        Object apply(UserAccount user, I input) throws Exception;
    }

    public <I> void execute(AiTaskMessage message, Class<I> inputType, Work<I> work) {
        Long taskId = message.taskId();
        AiTask task = aiTaskRepository.selectById(taskId);
        if (task == null) {
            log.warn("AI task {} not found, dropping message", taskId);
            return;
        }
        if (task.getStatus() != null && task.getStatus().isTerminal()) {
            log.debug("AI task {} already terminal ({}), ignoring", taskId, task.getStatus());
            return;
        }

        if (task.getStatus() == AiTaskStatus.PENDING) {
            int rows = aiTaskRepository.transitionToRunning(taskId);
            if (rows == 0) {
                log.debug("AI task {} was claimed by another consumer", taskId);
                return;
            }
            task.setStatus(AiTaskStatus.RUNNING);
        }

        try {
            UserAccount user = task.getUserId() == null
                    ? null
                    : userAccountRepository.selectById(task.getUserId());
            I input = objectMapper.readValue(task.getInputJson(), inputType);
            Object result = work.apply(user, input);
            task.setResultJson(objectMapper.writeValueAsString(result));
            task.setStatus(AiTaskStatus.SUCCESS);
            task.setErrorMessage(null);
            aiTaskRepository.updateById(task);
            aiTaskNotifier.publishDone(taskId);
            log.info("AI task {} completed successfully", taskId);
        } catch (AiUnavailableException ex) {
            markFailed(task, "AI_UNAVAILABLE: " + ex.getMessage());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                markFailed(task, ex.getReason() == null ? ex.getMessage() : ex.getReason());
            } else {
                recordAttemptError(task, ex);
                throw ex;
            }
        } catch (RuntimeException ex) {
            recordAttemptError(task, ex);
            throw ex;
        } catch (Exception ex) {
            recordAttemptError(task, ex);
            throw new RuntimeException(ex);
        }
    }

    private void markFailed(AiTask task, String errorMessage) {
        task.setStatus(AiTaskStatus.FAILED);
        task.setErrorMessage(truncate(errorMessage));
        task.setRetryCount(nvl(task.getRetryCount()) + 1);
        aiTaskRepository.updateById(task);
        aiTaskNotifier.publishDone(task.getId());
        log.warn("AI task {} marked FAILED: {}", task.getId(), errorMessage);
    }

    private void recordAttemptError(AiTask task, Exception ex) {
        task.setErrorMessage(truncate(ex.getMessage()));
        task.setRetryCount(nvl(task.getRetryCount()) + 1);
        aiTaskRepository.updateById(task);
        log.warn("AI task {} attempt failed (retryCount={}): {}",
                task.getId(), task.getRetryCount(), ex.getMessage());
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_ERROR_MESSAGE_LENGTH
                ? value.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : value;
    }
}
