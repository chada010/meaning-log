package com.chad.meaninglog.client;

/**
 * Thrown when the upstream AI provider is unavailable (retries exhausted or circuit breaker open).
 * Terminal in the MQ consumer path — the task is marked FAILED without further retries.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
