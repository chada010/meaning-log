package com.chad.meaninglog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {

    /**
     * SSE 流式推送专用线程池。
     * SseEmitter 的写入必须在独立线程中执行，不能阻塞 Tomcat 的请求处理线程。
     * CachedThreadPool 按需创建线程，适合并发量不大的场景。
     */
    @Bean
    public ExecutorService sseExecutorService() {
        return Executors.newCachedThreadPool();
    }
}
