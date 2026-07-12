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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceTests {

    private static final String EMAIL = "alice@example.com";
    private static final String CODE_KEY = "email:verify:code:" + EMAIL;

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
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyCodeAtomicallyConsumesMatchingCodeUsingNormalizedEmail() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(CODE_KEY)), eq("123456"))).thenReturn(1L);
        EmailVerificationService service = service(redisTemplate, mailSender);

        service.verifyCode(" Alice@Example.COM ", " 123456 ");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(CODE_KEY)), eq("123456"));
    }

    private EmailVerificationService service(StringRedisTemplate redisTemplate, JavaMailSender mailSender) {
        EmailVerificationService service = new EmailVerificationService(redisTemplate, mailSender);
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@example.com");
        ReflectionTestUtils.setField(service, "codeTtlSeconds", 300L);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60L);
        return service;
    }
}
