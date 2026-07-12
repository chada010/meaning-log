package com.chad.meaninglog.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceTests {

    private static final String EMAIL = "alice@example.com";
    private static final String CODE_KEY = "email:verify:code:" + EMAIL;
    private static final String ATTEMPT_KEY = "email:verify:attempts:" + EMAIL;

    @Test
    @SuppressWarnings("unchecked")
    void sendCodeUsesNormalizedEmailForDeliveryAndRedisKeys() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("email:verify:cooldown:" + EMAIL)).thenReturn(false);
        EmailVerificationService service = service(redisTemplate, mailSender);

        service.sendCode(" Alice@Example.COM ");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getTo()).containsExactly(EMAIL);
        verify(valueOperations).set(eq(CODE_KEY), any(String.class), eq(Duration.ofSeconds(300)));
        verify(valueOperations).set("email:verify:cooldown:" + EMAIL, "1", Duration.ofSeconds(60));
        verify(redisTemplate).delete(ATTEMPT_KEY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyCodeAtomicallyConsumesMatchingCodeUsingNormalizedEmail() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(CODE_KEY, ATTEMPT_KEY)),
                eq("123456"),
                eq("5")
        )).thenReturn(1L);
        EmailVerificationService service = service(redisTemplate, mailSender);

        service.verifyCode(" Alice@Example.COM ", " 123456 ");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(CODE_KEY, ATTEMPT_KEY)),
                eq("123456"),
                eq("5")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyCodeRejectsAttemptsAfterTheConfiguredLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(CODE_KEY, ATTEMPT_KEY)),
                eq("123456"),
                eq("5")
        )).thenReturn(-1L);

        assertThatThrownBy(() -> service(redisTemplate, mock(JavaMailSender.class))
                .verifyCode(EMAIL, "123456"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verificationScriptChecksTheAttemptLimitBeforeAcceptingACode() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(CODE_KEY, ATTEMPT_KEY)),
                eq("123456"),
                eq("5")
        )).thenReturn(1L);

        service(redisTemplate, mock(JavaMailSender.class)).verifyCode(EMAIL, "123456");

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of(CODE_KEY, ATTEMPT_KEY)), eq("123456"), eq("5"));
        String script = scriptCaptor.getValue().getScriptAsString();
        assertThat(script.indexOf("local attempts = redis.call('get', KEYS[2])"))
                .isLessThan(script.indexOf("if stored == ARGV[1] then"));
    }

    private EmailVerificationService service(StringRedisTemplate redisTemplate, JavaMailSender mailSender) {
        EmailVerificationService service = new EmailVerificationService(redisTemplate, mailSender);
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@example.com");
        ReflectionTestUtils.setField(service, "codeTtlSeconds", 300L);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60L);
        ReflectionTestUtils.setField(service, "maxVerificationAttempts", 5);
        return service;
    }
}
