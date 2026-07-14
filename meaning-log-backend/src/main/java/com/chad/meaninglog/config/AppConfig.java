package com.chad.meaninglog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * SSE streaming executor with bounded concurrency and queue capacity.
     */
    @Bean
    public ThreadPoolTaskExecutor sseExecutorService(
            @Value("${app.sse.executor.pool-size:8}") int poolSize,
            @Value("${app.sse.executor.queue-capacity:32}") int queueCapacity,
            @Value("${app.sse.executor.shutdown-await-seconds:30}") int shutdownAwaitSeconds
    ) {
        if (poolSize < 1 || queueCapacity < 1 || shutdownAwaitSeconds < 0) {
            throw new IllegalArgumentException("SSE executor properties must use positive pool and queue sizes");
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(shutdownAwaitSeconds);
        executor.initialize();
        return executor;
    }

    @Bean(destroyMethod = "stop")
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
