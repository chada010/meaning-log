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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

        // 事务提交后再投递 MQ，避免 dual-write：
        // 若在事务内直接 send，事务回滚时消息已发出会成为消费不到 DB 记录的幽灵任务；
        // 消费者也可能在事务提交前先 selectById 拿不到（读不到未提交），直接 drop message。
        AiTaskMessage message = new AiTaskMessage(task.getId(), type);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiTaskProducer.send(message);
                }
            });
        } else {
            aiTaskProducer.send(message);
        }
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
