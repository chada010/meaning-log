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

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiTaskExecutor {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final AiTaskRepository aiTaskRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;
    private final AiTaskNotifier aiTaskNotifier;
    private final Clock businessClock;

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
        if (task.getStatus() != AiTaskStatus.PENDING) {
            log.debug("AI task {} is {}, ignoring duplicate message", taskId, task.getStatus());
            return;
        }

        int rows = aiTaskRepository.transitionToRunning(taskId, LocalDateTime.now(businessClock));
        if (rows == 0) {
            log.debug("AI task {} was claimed by another consumer", taskId);
            return;
        }

        try {
            UserAccount user = task.getUserId() == null
                    ? null
                    : userAccountRepository.selectById(task.getUserId());
            I input = objectMapper.readValue(task.getInputJson(), inputType);
            Object result = work.apply(user, input);
            String resultJson = objectMapper.writeValueAsString(result);
            int completed = aiTaskRepository.completeRunning(
                    taskId, resultJson, LocalDateTime.now(businessClock));
            if (completed > 0) {
                aiTaskNotifier.publishDone(taskId);
                log.info("AI task {} completed successfully", taskId);
            } else {
                log.warn("AI task {} finished work after execution ownership was lost", taskId);
            }
        } catch (AiUnavailableException ex) {
            markFailed(taskId, "AI_UNAVAILABLE: " + ex.getMessage());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                markFailed(taskId, ex.getReason() == null ? ex.getMessage() : ex.getReason());
            } else {
                returnToPending(taskId, ex);
                throw ex;
            }
        } catch (RuntimeException ex) {
            returnToPending(taskId, ex);
            throw ex;
        } catch (Exception ex) {
            returnToPending(taskId, ex);
            throw new RuntimeException(ex);
        }
    }

    private void markFailed(Long taskId, String errorMessage) {
        int rows = aiTaskRepository.failRunning(
                taskId, truncate(errorMessage), LocalDateTime.now(businessClock));
        if (rows > 0) {
            aiTaskNotifier.publishDone(taskId);
            log.warn("AI task {} marked FAILED: {}", taskId, errorMessage);
        } else {
            log.debug("AI task {} failure ignored because execution ownership was lost", taskId);
        }
    }

    private void returnToPending(Long taskId, Exception ex) {
        int rows = aiTaskRepository.returnRunningToPending(
                taskId, truncate(ex.getMessage()), LocalDateTime.now(businessClock));
        if (rows > 0) {
            log.warn("AI task {} attempt failed and returned to PENDING: {}", taskId, ex.getMessage());
        } else {
            log.warn("AI task {} retry state was not restored because execution ownership was lost", taskId);
        }
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
