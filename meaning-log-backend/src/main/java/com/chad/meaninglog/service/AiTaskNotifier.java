package com.chad.meaninglog.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskNotifier {

    private static final String CHANNEL_PREFIX = "ai.task.done.";
    private static final String CHANNEL_PATTERN = "ai.task.done.*";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @PostConstruct
    void subscribe() {
        MessageListener listener = (message, pattern) -> {
            String channel = new String(message.getChannel());
            if (!channel.startsWith(CHANNEL_PREFIX)) {
                return;
            }
            String suffix = channel.substring(CHANNEL_PREFIX.length());
            try {
                Long taskId = Long.parseLong(suffix);
                dispatchDone(taskId);
            } catch (NumberFormatException ex) {
                log.warn("Ignoring malformed AI task channel: {}", channel);
            }
        };
        redisMessageListenerContainer.addMessageListener(listener, new PatternTopic(CHANNEL_PATTERN));
    }

    public void publishDone(Long taskId) {
        if (taskId == null) {
            return;
        }
        try {
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + taskId, "done");
        } catch (Exception ex) {
            log.warn("Failed to publish AI task done notification for {}", taskId, ex);
            dispatchDone(taskId);
        }
    }

    public void register(Long taskId, SseEmitter emitter) {
        emitters.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(taskId, emitter);
        });
        emitter.onError(err -> removeEmitter(taskId, emitter));
    }

    private void dispatchDone(Long taskId) {
        List<SseEmitter> waiting = emitters.remove(taskId);
        if (waiting == null) {
            return;
        }
        for (SseEmitter emitter : waiting) {
            try {
                emitter.send(SseEmitter.event().name("done").data(String.valueOf(taskId)));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    private void removeEmitter(Long taskId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(taskId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(taskId, list);
        }
    }
}
