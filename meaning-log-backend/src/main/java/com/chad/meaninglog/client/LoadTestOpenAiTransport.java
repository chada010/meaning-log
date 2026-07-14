package com.chad.meaninglog.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 压测专用 Mock：模拟"上游 AI 5s 阻塞"的场景，用于隔离变量对比同步阻塞 vs MQ 异步的架构差异。
 * 仅在 loadtest profile 下加载，不会污染 dev / prod。
 */
@Component
@Profile("loadtest")
@Slf4j
public class LoadTestOpenAiTransport extends OpenAiTransport {

    private static final long MOCK_UPSTREAM_DELAY_MS = 5_000L;
    private static final String MOCK_JSON =
            "{\"title\":\"Loadtest\",\"summary\":\"Loadtest mock summary.\",\"tags\":[\"loadtest\"]}";

    public LoadTestOpenAiTransport(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        super(restClientBuilder, objectMapper, "mock-key", "http://loadtest.invalid", "mock-model");
    }

    @Override
    public String complete(List<Map<String, Object>> messages, int maxTokens, double temperature) {
        sleepMocked();
        return MOCK_JSON;
    }

    @Override
    public void stream(
            List<Map<String, Object>> messages,
            int maxTokens,
            double temperature,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        sleepMocked();
        onChunk.accept(MOCK_JSON);
        onComplete.run();
    }

    private void sleepMocked() {
        try {
            Thread.sleep(MOCK_UPSTREAM_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
