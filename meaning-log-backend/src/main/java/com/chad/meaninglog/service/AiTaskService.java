package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.AiTaskResponse;
import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.entity.AiTaskType;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.mq.AiTaskMessage;
import com.chad.meaninglog.mq.producer.AiTaskProducer;
import com.chad.meaninglog.repository.AiTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskService {

    private final AiTaskRepository aiTaskRepository;
    private final AiTaskProducer aiTaskProducer;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiTask create(UserAccount user, AiTaskType type, Object input) {
        AiTask task = new AiTask();
        task.setUserId(user == null ? null : user.getId());
        task.setTaskType(type);
        task.setStatus(AiTaskStatus.PENDING);
        task.setInputJson(serialize(input));
        task.setRetryCount(0);
        aiTaskRepository.insert(task);

        aiTaskProducer.send(new AiTaskMessage(task.getId(), type));
        return task;
    }

    @Transactional(readOnly = true)
    public AiTaskResponse findByIdForUser(UserAccount user, Long taskId) {
        AiTask task = aiTaskRepository.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI task not found");
        }
        Long ownerId = task.getUserId();
        Long requesterId = user == null ? null : user.getId();
        if (ownerId != null && !ownerId.equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI task not found");
        }
        return AiTaskResponse.from(task);
    }

    private String serialize(Object input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI task input", e);
        }
    }
}
