package com.chad.meaninglog.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@Profile("!loadtest")
@Slf4j
public class OpenAiTransport {

    static final String INSTANCE_NAME = "deepseek";

    private final RestClient restClient;
    private final String apiKey;
    private final OpenAiRequestFactory requestFactory;
    private final OpenAiResponseParser responseParser;

    public OpenAiTransport(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.base-url:https://api.deepseek.com/v1}") String baseUrl,
            @Value("${app.ai.model:deepseek-chat}") String model
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.requestFactory = new OpenAiRequestFactory(model);
        this.responseParser = new OpenAiResponseParser(objectMapper);
    }

    public OpenAiResponseParser responseParser() {
        return responseParser;
    }

    @Retry(name = INSTANCE_NAME)
    @CircuitBreaker(name = INSTANCE_NAME, fallbackMethod = "completeFallback")
    public String complete(List<Map<String, Object>> messages, int maxTokens, double temperature) {
        ensureConfigured();
        try {
            JsonNode response = request(requestFactory.create(messages, maxTokens, temperature, false))
                    .retrieve()
                    .body(JsonNode.class);
            return responseParser.extractMessageContent(response);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI request failed: " + responseParser.extractProviderError(ex),
                        ex
                );
            }
            throw ex;
        }
    }

    @Retry(name = INSTANCE_NAME)
    @CircuitBreaker(name = INSTANCE_NAME, fallbackMethod = "streamFallback")
    public void stream(
            List<Map<String, Object>> messages,
            int maxTokens,
            double temperature,
            Consumer<String> onChunk,
            Runnable onComplete
    ) {
        ensureConfigured();
        try {
            request(requestFactory.create(messages, maxTokens, temperature, true)).exchange((req, resp) -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank() || !line.startsWith("data:")) {
                            continue;
                        }
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) {
                            onComplete.run();
                            break;
                        }
                        try {
                            String content = responseParser.extractStreamContent(data);
                            if (content != null) {
                                onChunk.accept(content);
                            }
                        } catch (Exception ignored) {
                            // 跳过无法解析的 SSE 行
                        }
                    }
                }
                return null;
            });
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI stream request failed: " + responseParser.extractProviderError(ex),
                        ex
                );
            }
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    public String completeFallback(
            List<Map<String, Object>> messages,
            int maxTokens,
            double temperature,
            Throwable throwable
    ) {
        throw asAiUnavailable("chat completion", throwable);
    }

    @SuppressWarnings("unused")
    public void streamFallback(
            List<Map<String, Object>> messages,
            int maxTokens,
            double temperature,
            Consumer<String> onChunk,
            Runnable onComplete,
            Throwable throwable
    ) {
        throw asAiUnavailable("chat stream", throwable);
    }

    private AiUnavailableException asAiUnavailable(String phase, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            log.warn("AI {} rejected by open circuit breaker: {}", phase, throwable.getMessage());
            return new AiUnavailableException("AI circuit breaker open (" + phase + ")", throwable);
        }
        if (throwable instanceof ResourceAccessException) {
            log.warn("AI {} connection issue: {}", phase, throwable.getMessage());
            return new AiUnavailableException("AI connection error: " + throwable.getMessage(), throwable);
        }
        if (throwable instanceof RestClientResponseException rre) {
            log.warn("AI {} upstream error status={}: {}", phase, rre.getStatusCode(), rre.getMessage());
            return new AiUnavailableException(
                    "AI upstream " + rre.getStatusCode() + ": " + responseParser.extractProviderError(rre),
                    throwable);
        }
        if (throwable instanceof IOException) {
            return new AiUnavailableException("AI I/O error: " + throwable.getMessage(), throwable);
        }
        if (throwable instanceof RuntimeException re) {
            throw re;
        }
        return new AiUnavailableException("AI request failed: " + throwable.getMessage(), throwable);
    }

    private RestClient.RequestBodySpec request(Map<String, Object> request) {
        return restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(request);
    }

    private void ensureConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI API key is not configured. Please set DEEPSEEK_API_KEY before starting the backend."
            );
        }
    }
}
