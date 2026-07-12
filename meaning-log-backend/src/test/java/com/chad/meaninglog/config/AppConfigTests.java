package com.chad.meaninglog.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTests {

    @Test
    void createsNamedFixedSizeExecutorWithBoundedQueue() {
        ThreadPoolTaskExecutor executor = new AppConfig().sseExecutorService(2, 3, 1);

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("sse-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(3);
        } finally {
            executor.shutdown();
        }
    }
}
