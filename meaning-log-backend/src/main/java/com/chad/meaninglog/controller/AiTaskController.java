package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiTaskResponse;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.AiTaskNotifier;
import com.chad.meaninglog.service.AiTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static com.chad.meaninglog.web.WebConstants.LOCAL_FRONTEND_ORIGIN;

@CrossOrigin(origins = LOCAL_FRONTEND_ORIGIN)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/tasks")
public class AiTaskController {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final AiTaskService aiTaskService;
    private final AiTaskNotifier aiTaskNotifier;

    @GetMapping("/{taskId}")
    public AiTaskResponse findTask(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long taskId
    ) {
        return aiTaskService.findByIdForUser(user, taskId);
    }

    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskDone(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long taskId
    ) {
        AiTaskResponse task = aiTaskService.findByIdForUser(user, taskId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        if (task.status() != null && task.status().isTerminal()) {
            sendDoneAndComplete(emitter, taskId);
            return emitter;
        }

        aiTaskNotifier.register(taskId, emitter);
        AiTaskResponse recheck = aiTaskService.findByIdForUser(user, taskId);
        if (recheck.status() == AiTaskStatus.SUCCESS || recheck.status() == AiTaskStatus.FAILED) {
            aiTaskNotifier.publishDone(taskId);
        }
        return emitter;
    }

    private void sendDoneAndComplete(SseEmitter emitter, Long taskId) {
        try {
            emitter.send(SseEmitter.event().name("done").data(String.valueOf(taskId)));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
