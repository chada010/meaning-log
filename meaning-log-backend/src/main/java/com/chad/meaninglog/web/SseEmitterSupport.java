package com.chad.meaninglog.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.chad.meaninglog.web.WebConstants.CACHE_CONTROL_HEADER;
import static com.chad.meaninglog.web.WebConstants.NO_BUFFERING_VALUE;
import static com.chad.meaninglog.web.WebConstants.NO_CACHE_VALUE;
import static com.chad.meaninglog.web.WebConstants.SSE_BUFFERING_HEADER;
import static com.chad.meaninglog.web.WebConstants.SSE_DONE_EVENT;
import static com.chad.meaninglog.web.WebConstants.SSE_TIMEOUT_MS;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseEmitterSupport {

    private final ThreadPoolTaskExecutor sseExecutorService;

    public SseEmitter create(HttpServletResponse response) {
        response.setHeader(SSE_BUFFERING_HEADER, NO_BUFFERING_VALUE);
        response.setHeader(CACHE_CONTROL_HEADER, NO_CACHE_VALUE);
        return new SseEmitter(SSE_TIMEOUT_MS);
    }

    public void submit(SseEmitter emitter, SseTask task) {
        try {
            sseExecutorService.submit(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
        } catch (TaskRejectedException ex) {
            log.warn(
                    "SSE task rejected: activeThreads={}, poolSize={}, queueSize={}",
                    sseExecutorService.getActiveCount(),
                    sseExecutorService.getPoolSize(),
                    sseExecutorService.getQueueSize()
            );
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI streaming service is busy. Please try again shortly.",
                    ex
            );
        }
    }

    public void sendData(SseEmitter emitter, Object data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void completeWithDone(SseEmitter emitter) {
        completeWithEvent(emitter, SSE_DONE_EVENT, "");
    }

    public void completeWithEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    @FunctionalInterface
    public interface SseTask {
        void run() throws Exception;
    }
}
