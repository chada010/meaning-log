package com.chad.meaninglog.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class OpenAiTransport {

    private final RestClient restClient;
    private final String apiKey;
    private final OpenAiRequestFactory requestFactory;
    private final OpenAiResponseParser responseParser;

    OpenAiTransport(
            RestClient restClient,
            String apiKey,
            OpenAiRequestFactory requestFactory,
            OpenAiResponseParser responseParser
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.requestFactory = requestFactory;
        this.responseParser = responseParser;
    }

    String complete(List<Map<String, Object>> messages, int maxTokens, double temperature) {
        ensureConfigured();
        try {
            JsonNode response = request(requestFactory.create(messages, maxTokens, temperature, false))
                    .retrieve()
                    .body(JsonNode.class);
            return responseParser.extractMessageContent(response);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI request failed: " + responseParser.extractProviderError(ex),
                    ex
            );
        }
    }

    void stream(
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
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI stream request failed: " + responseParser.extractProviderError(ex),
                    ex
            );
        }
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
