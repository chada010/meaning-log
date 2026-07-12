package com.chad.meaninglog.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

final class OpenAiResponseParser {

    private final ObjectMapper objectMapper;

    OpenAiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String extractMessageContent(JsonNode response) {
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isTextual() && !content.asText().isBlank()) {
            return stripMarkdownFence(content.asText());
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response did not contain message content");
    }

    String extractStreamContent(String data) throws Exception {
        JsonNode content = objectMapper.readTree(data).path("choices").path(0).path("delta").path("content");
        return content.isTextual() && !content.asText().isEmpty() ? content.asText() : null;
    }

    <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to parse AI JSON output: " + value, ex);
        }
    }

    String extractProviderError(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            JsonNode message = body.path("error").path("message");
            if (message.isTextual()) {
                return message.asText();
            }
            JsonNode code = body.path("code");
            JsonNode msg = body.path("message");
            if (code.isTextual() || msg.isTextual()) {
                return code.asText() + " " + msg.asText();
            }
        } catch (Exception ignored) {
            // Fall through to raw response body.
        }
        return ex.getResponseBodyAsString();
    }

    private String stripMarkdownFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        return trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }
}
