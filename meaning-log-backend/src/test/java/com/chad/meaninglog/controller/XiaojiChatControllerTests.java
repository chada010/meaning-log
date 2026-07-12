package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.AiChatRequest;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.service.XiaojiChatService;
import com.chad.meaninglog.web.SseEmitterSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class XiaojiChatControllerTests {

    @Test
    void saturatedExecutorRejectsChatStreamBeforePersistingUserMessage() throws InterruptedException {
        ThreadPoolTaskExecutor executor = saturatedExecutor();
        SseEmitterSupport sseEmitterSupport = new SseEmitterSupport(executor);
        XiaojiChatService xiaojiChatService = mock(XiaojiChatService.class);
        AiService aiService = mock(AiService.class);
        XiaojiChatController controller = new XiaojiChatController(xiaojiChatService, aiService, sseEmitterSupport);

        try {
            saturate(sseEmitterSupport);

            assertThatThrownBy(() -> controller.chatStream(new UserAccount(), request(), new MockHttpServletResponse()))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

            verifyNoInteractions(xiaojiChatService, aiService);
        } finally {
            executor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor saturatedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
        return executor;
    }

    private void saturate(SseEmitterSupport support) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        support.submit(new SseEmitter(), () -> {
            started.countDown();
            release.await();
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        support.submit(new SseEmitter(), () -> { });
    }

    private AiChatRequest request() {
        AiChatRequest request = new AiChatRequest();
        request.setMessage("hello");
        return request;
    }
}
