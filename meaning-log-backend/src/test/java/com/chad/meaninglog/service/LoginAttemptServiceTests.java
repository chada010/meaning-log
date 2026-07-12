package com.chad.meaninglog.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginAttemptServiceTests {

    private static final String PRINCIPAL = "alice@example.com";
    private static final String SOURCE = "198.51.100.10";
    private static final List<String> ATTEMPT_KEYS = List.of(
            "auth:login:attempts:source:" + SOURCE,
            "auth:login:attempts:principal-source:" + PRINCIPAL + ":" + SOURCE,
            "auth:login:attempts:principal:" + PRINCIPAL
    );

    @Test
    @SuppressWarnings("unchecked")
    void rejectsLoginsWhenAnyAtomicLimitIsReached() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(ATTEMPT_KEYS),
                eq("900"),
                eq("20"),
                eq("5"),
                eq("50")
        )).thenReturn(0L);
        LoginAttemptService service = service(redisTemplate);

        assertThatThrownBy(() -> service.reserveAttempt(PRINCIPAL, SOURCE))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void clearsAllLoginFailureStateAfterSuccessfulAuthentication() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        LoginAttemptService service = service(redisTemplate);

        service.clearFailures(PRINCIPAL, SOURCE);

        verify(redisTemplate).delete(ATTEMPT_KEYS);
    }

    private LoginAttemptService service(StringRedisTemplate redisTemplate) {
        LoginAttemptService service = new LoginAttemptService(redisTemplate);
        ReflectionTestUtils.setField(service, "windowSeconds", 900L);
        ReflectionTestUtils.setField(service, "maxAttemptsPerSource", 20);
        ReflectionTestUtils.setField(service, "maxAttemptsPerPrincipalSource", 5);
        ReflectionTestUtils.setField(service, "maxAttemptsPerPrincipal", 50);
        return service;
    }
}
