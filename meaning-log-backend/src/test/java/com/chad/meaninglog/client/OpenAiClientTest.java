package com.chad.meaninglog.client;

import com.chad.meaninglog.dto.LogAiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientTest {

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;
    private OpenAiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", this::handleCompletion);
        server.start();
        client = new OpenAiClient(
                RestClient.builder(),
                new ObjectMapper(),
                "test-api-key",
                "http://localhost:" + server.getAddress().getPort(),
                "test-model"
        );
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void analyzeLog_sendsExistingRequestShapeAndParsesFencedJson() {
        LogAiResult result = client.analyzeLog("2026-07-12", "散步", "平静", "傍晚散步", List.of());

        assertThat(result.title()).isEqualTo("晚风散步");
        assertThat(result.summary()).isEqualTo("傍晚慢慢走了一段路。");
        assertThat(result.tags()).containsExactly("日常", "散步");
        assertThat(authorization.get()).isEqualTo("Bearer test-api-key");
        assertThat(requestBody.get())
                .contains("\"model\":\"test-model\"")
                .contains("\"max_tokens\":900")
                .doesNotContain("\"stream\"");
    }

    @Test
    void streamChatCompletion_forwardsContentChunksAndCompletesOnlyAtDoneEvent() {
        List<String> chunks = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean();

        client.streamChatCompletion(
                List.of(java.util.Map.of("role", "user", "content", "你好")),
                123,
                0.5,
                chunks::add,
                () -> completed.set(true)
        );

        assertThat(chunks).containsExactly("第一段", "第二段");
        assertThat(completed).isTrue();
        assertThat(requestBody.get())
                .contains("\"max_tokens\":123")
                .contains("\"temperature\":0.5")
                .contains("\"stream\":true");
    }

    private void handleCompletion(HttpExchange exchange) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        boolean streaming = requestBody.get().contains("\"stream\":true");
        String response = streaming ? streamResponse() : completionResponse();

        exchange.getResponseHeaders().set("Content-Type", streaming
                ? "text/event-stream; charset=utf-8"
                : "application/json; charset=utf-8");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String completionResponse() {
        return "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"title\\\":\\\"晚风散步\\\",\\\"summary\\\":\\\"傍晚慢慢走了一段路。\\\",\\\"tags\\\":[\\\"日常\\\",\\\"散步\\\"]}\\n```\"}}]}";
    }

    private String streamResponse() {
        return """
                data: {"choices":[{"delta":{"content":"第一段"}}]}

                data: malformed

                event: ping

                data: {"choices":[{"delta":{"content":"第二段"}}]}

                data: [DONE]

                """;
    }
}
