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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    void attemptsAreLimitedPerResolvedClientAddress() {
        storeCode("123456");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verifyCode(EMAIL, "000000", SOURCE_A))
                    .isInstanceOf(ResponseStatusException.class);
        }

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "123456", SOURCE_A))
                .isInstanceOf(ResponseStatusException.class);
        service.verifyCode(EMAIL, "123456", SOURCE_B);
    }

    @Test
    void attemptsFromDifferentClientAddressesShareTheCodeTotalLimit() {
        service = service(redisTemplate, 5);
        storeCode("123456");
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> service.verifyCode(EMAIL, "000000", SOURCE_A))
                    .isInstanceOf(ResponseStatusException.class);
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> service.verifyCode(EMAIL, "000000", SOURCE_B))
                    .isInstanceOf(ResponseStatusException.class);
        }

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "123456", "198.51.100.12"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void attemptStateDoesNotApplyToANewCode() {
        storeCode("123456");
        assertThatThrownBy(() -> service.verifyCode(EMAIL, "000000", SOURCE_A))
                .isInstanceOf(ResponseStatusException.class);
        storeCode("654321");
        service.verifyCode(EMAIL, "654321", SOURCE_A);
        assertThat(redisTemplate.hasKey(CODE_KEY)).isFalse();
    }

    @Test
    void simultaneousSendCodeRequestsReserveOnlyOneCooldown() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        service = service(redisTemplate, mailSender, 20);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> results = List.of(
                    executor.submit(sendCodeAfter(start)),
                    executor.submit(sendCodeAfter(start))
            );
            start.countDown();

            long successfulRequests = results.stream()
                    .map(this::awaitResult)
                    .filter(result -> result == null)
                    .count();

            assertThat(successfulRequests).isEqualTo(1);
            verify(mailSender, times(1)).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        } finally {
            executor.shutdownNow();
        }
    }

    private void storeCode(String code) {
        redisTemplate.opsForValue().set(CODE_KEY, code, Duration.ofSeconds(300));
    }

    private EmailVerificationService service(StringRedisTemplate template) {
        return service(template, 20);
    }

    private EmailVerificationService service(StringRedisTemplate template, int maxTotalAttempts) {
        return service(template, mock(JavaMailSender.class), maxTotalAttempts);
    }

    private EmailVerificationService service(
            StringRedisTemplate template,
            JavaMailSender mailSender,
            int maxTotalAttempts
    ) {
        EmailVerificationService emailService = new EmailVerificationService(template, mailSender);
        ReflectionTestUtils.setField(emailService, "mailFrom", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "codeTtlSeconds", 300L);
        ReflectionTestUtils.setField(emailService, "cooldownSeconds", 60L);
        ReflectionTestUtils.setField(emailService, "maxVerificationAttempts", 5);
        ReflectionTestUtils.setField(emailService, "maxTotalVerificationAttempts", maxTotalAttempts);
        return emailService;
    }

    private Callable<Throwable> sendCodeAfter(CountDownLatch start) {
        return () -> {
            start.await();
            try {
                service.sendCode(EMAIL);
                return null;
            } catch (RuntimeException ex) {
                return ex;
            }
        };
    }

    private Throwable awaitResult(Future<Throwable> result) {
        try {
            return result.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ex;
        } catch (ExecutionException ex) {
            return ex.getCause();
        }
    }
}
