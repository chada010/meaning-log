package com.chad.meaninglog.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationRedisIntegrationTests {

    private static final String EMAIL = "alice@example.com";
    private static final String CODE_KEY = "email:verify:code:" + EMAIL;
    private static final String SOURCE_A = "198.51.100.10";
    private static final String SOURCE_B = "198.51.100.11";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        service = service(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void matchingCodeIsConsumedAndCannotBeReused() {
        storeCode("123456");

        service.verifyCode(EMAIL, "123456", SOURCE_A);

        assertThat(redisTemplate.hasKey(CODE_KEY)).isFalse();
        assertThatThrownBy(() -> service.verifyCode(EMAIL, "123456", SOURCE_A))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void attemptsAreLimitedPerSourceAndResetWhenTheCodeChanges() {
        storeCode("123456");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verifyCode(EMAIL, "000000", SOURCE_A))
                    .isInstanceOf(ResponseStatusException.class);
        }

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "123456", SOURCE_A))
                .isInstanceOf(ResponseStatusException.class);
        service.verifyCode(EMAIL, "123456", SOURCE_B);

        storeCode("654321");
        service.verifyCode(EMAIL, "654321", SOURCE_A);
        assertThat(redisTemplate.hasKey(CODE_KEY)).isFalse();
    }

    private void storeCode(String code) {
        redisTemplate.opsForValue().set(CODE_KEY, code, Duration.ofSeconds(300));
    }

    private EmailVerificationService service(StringRedisTemplate template) {
        EmailVerificationService emailService = new EmailVerificationService(template, mock(JavaMailSender.class));
        ReflectionTestUtils.setField(emailService, "mailFrom", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "codeTtlSeconds", 300L);
        ReflectionTestUtils.setField(emailService, "cooldownSeconds", 60L);
        ReflectionTestUtils.setField(emailService, "maxVerificationAttempts", 5);
        return emailService;
    }
}
