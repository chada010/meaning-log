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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationRedisIntegrationTests {

    private static final String EMAIL = "alice@example.com";
    private static final String CODE_KEY = "email:verify:code:" + EMAIL;
    private static final String COOLDOWN_KEY = "email:verify:cooldown:" + EMAIL;
    private static final String SOURCE_A = "198.51.100.10";
    private static final String SOURCE_B = "198.51.100.11";
    private static final String SOURCE_C = "198.51.100.12";
    private static final String SEND_SOURCE_ATTEMPT_KEY_PREFIX = "email:verify:send:source:";
    private static final String SEND_GLOBAL_ATTEMPT_KEY = "email:verify:send:global";

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
        redisTemplate.delete(List.of(
                CODE_KEY,
                COOLDOWN_KEY,
                SEND_SOURCE_ATTEMPT_KEY_PREFIX + SOURCE_A,
                SEND_SOURCE_ATTEMPT_KEY_PREFIX + SOURCE_B,
                SEND_SOURCE_ATTEMPT_KEY_PREFIX + SOURCE_C,
                SEND_GLOBAL_ATTEMPT_KEY
        ));
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
    void matchingCodeRemainsAcceptedAfterSourceAttemptLimit() {
        storeCode("123456");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verifyCode(EMAIL, "000000", SOURCE_A))
                    .isInstanceOf(ResponseStatusException.class);
        }

        service.verifyCode(EMAIL, "123456", SOURCE_A);
    }

    @Test
    void matchingCodeRemainsAcceptedAfterDistributedAttemptLimit() {
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

        service.verifyCode(EMAIL, "123456", "198.51.100.12");
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
        service = service(redisTemplate, mailSender, 20, 5, 100);
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

    @Test
    void failedRequestDoesNotReleaseAReplacementCooldownReservation() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch firstDeliveryStarted = new CountDownLatch(1);
        CountDownLatch failFirstDelivery = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (deliveries.incrementAndGet() == 1) {
                firstDeliveryStarted.countDown();
                assertThat(failFirstDelivery.await(5, TimeUnit.SECONDS)).isTrue();
                throw new IllegalStateException("SMTP unavailable");
            }
            return null;
        }).when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        service = service(redisTemplate, mailSender, 20, 5, 100);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> firstRequest = executor.submit(() -> service.sendCode(EMAIL, SOURCE_A));
            assertThat(firstDeliveryStarted.await(5, TimeUnit.SECONDS)).isTrue();

            redisTemplate.delete(COOLDOWN_KEY);
            service.sendCode(EMAIL, SOURCE_B);
            failFirstDelivery.countDown();

            assertThatThrownBy(firstRequest::get)
                    .isInstanceOf(ExecutionException.class);
            assertThat(redisTemplate.hasKey(COOLDOWN_KEY)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sendCodeLimitsRequestsFromOneSource() {
        service = service(redisTemplate, mock(JavaMailSender.class), 20, 2, 100);

        service.sendCode("first@example.com", SOURCE_A);
        service.sendCode("second@example.com", SOURCE_A);

        assertThatThrownBy(() -> service.sendCode("third@example.com", SOURCE_A))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sendCodeLimitsRequestsAcrossSourcesGlobally() {
        service = service(redisTemplate, mock(JavaMailSender.class), 20, 100, 2);

        service.sendCode("global-first@example.com", SOURCE_A);
        service.sendCode("global-second@example.com", SOURCE_B);

        assertThatThrownBy(() -> service.sendCode("global-third@example.com", SOURCE_C))
                .isInstanceOf(ResponseStatusException.class);
    }

    private void storeCode(String code) {
        redisTemplate.opsForValue().set(CODE_KEY, code, Duration.ofSeconds(300));
    }

    private EmailVerificationService service(StringRedisTemplate template) {
        return service(template, 20);
    }

    private EmailVerificationService service(StringRedisTemplate template, int maxTotalAttempts) {
        return service(template, mock(JavaMailSender.class), maxTotalAttempts, 5, 100);
    }

    private EmailVerificationService service(
            StringRedisTemplate template,
            JavaMailSender mailSender,
            int maxTotalAttempts,
            int maxSendAttemptsPerSource,
            int maxSendAttemptsGlobal
    ) {
        EmailVerificationService emailService = new EmailVerificationService(template, mailSender);
        ReflectionTestUtils.setField(emailService, "mailFrom", "noreply@example.com");
        ReflectionTestUtils.setField(emailService, "codeTtlSeconds", 300L);
        ReflectionTestUtils.setField(emailService, "cooldownSeconds", 60L);
        ReflectionTestUtils.setField(emailService, "maxVerificationAttempts", 5);
        ReflectionTestUtils.setField(emailService, "maxTotalVerificationAttempts", maxTotalAttempts);
        ReflectionTestUtils.setField(emailService, "maxSendAttemptsPerSource", maxSendAttemptsPerSource);
        ReflectionTestUtils.setField(emailService, "maxSendAttemptsGlobal", maxSendAttemptsGlobal);
        ReflectionTestUtils.setField(emailService, "sendAttemptWindowSeconds", 60L);
        return emailService;
    }

    private Callable<Throwable> sendCodeAfter(CountDownLatch start) {
        return () -> {
            start.await();
            try {
                service.sendCode(EMAIL, SOURCE_A);
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
