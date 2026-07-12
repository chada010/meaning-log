package com.chad.meaninglog.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseEmitterSupportTests {

    @Test
    void rejectsStreamingRequestWithServiceUnavailableWhenExecutorIsSaturated() throws InterruptedException {
        ThreadPoolTaskExecutor executor = boundedExecutor();
        SseEmitterSupport support = new SseEmitterSupport(executor);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            support.submit(new SseEmitter(), () -> {
                started.countDown();
                release.await();
            });
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

            support.submit(new SseEmitter(), () -> { });

            assertThatThrownBy(() -> support.submit(new SseEmitter(), () -> { }))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    )
                    .hasMessageContaining("AI streaming service is busy");
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void releasesReservedCapacityAfterTaskCompletes() throws InterruptedException {
        ThreadPoolTaskExecutor executor = boundedExecutor();
        SseEmitterSupport support = new SseEmitterSupport(executor);
        CountDownLatch completed = new CountDownLatch(1);

        try (SseEmitterSupport.Submission submission = support.reserveSubmission()) {
            support.submit(submission, new SseEmitter(), completed::countDown);
        }
        assertThat(completed.await(1, TimeUnit.SECONDS)).isTrue();

        try (SseEmitterSupport.Submission ignored = support.reserveSubmission()) {
            assertThat(ignored).isNotNull();
        } finally {
            executor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor boundedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
        return executor;
    }
}
