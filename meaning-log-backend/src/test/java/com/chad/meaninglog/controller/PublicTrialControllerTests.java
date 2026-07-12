package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.TrialAnalyzeRequest;
import com.chad.meaninglog.service.AiRateLimiter;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.web.ClientIpResolver;
import com.chad.meaninglog.web.SseEmitterSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PublicTrialControllerTests {

    @Test
    void saturatedExecutorRejectsTrialStreamBeforeRateLimitIsConsumed() throws InterruptedException {
        ThreadPoolTaskExecutor executor = saturatedExecutor();
        SseEmitterSupport sseEmitterSupport = new SseEmitterSupport(executor);
        AiRateLimiter aiRateLimiter = mock(AiRateLimiter.class);
        AiService aiService = mock(AiService.class);
        PublicTrialController controller = new PublicTrialController(
                aiService, aiRateLimiter, sseEmitterSupport, new ClientIpResolver("")
        );

        try {
            saturate(sseEmitterSupport);

            assertThatThrownBy(() -> controller.analyzeStream(request(), httpRequest(), new MockHttpServletResponse()))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

            verifyNoInteractions(aiRateLimiter, aiService);
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

    private TrialAnalyzeRequest request() {
        TrialAnalyzeRequest request = new TrialAnalyzeRequest();
        request.setTitle("title");
        request.setContent("content");
        request.setLogDate(LocalDate.now());
        return request;
    }

    private MockHttpServletRequest httpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.20");
        return request;
    }
}
