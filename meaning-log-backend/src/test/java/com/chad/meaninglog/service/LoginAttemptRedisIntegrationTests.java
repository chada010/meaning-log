package com.chad.meaninglog.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class LoginAttemptRedisIntegrationTests {

    private static final String PRINCIPAL = "alice@example.com";
    private static final String SOURCE = "198.51.100.10";
    private static final List<String> ATTEMPT_KEYS = List.of(
            "auth:login:attempts:source:" + SOURCE,
            "auth:login:attempts:principal-source:" + PRINCIPAL + ":" + SOURCE
    );

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.delete(ATTEMPT_KEYS);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void simultaneousAttemptsReserveOnlyOneSourceQuota() throws Exception {
        LoginAttemptService service = service(1, 5);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> reserveAfterStart(service, start));
            Future<Boolean> second = executor.submit(() -> reserveAfterStart(service, start));
            start.countDown();

            int successfulReservations = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertThat(successfulReservations).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void successfulLoginClearsSourceAndPrincipalSourceAttemptState() {
        LoginAttemptService service = service(20, 5);
        service.reserveAttempt(PRINCIPAL, SOURCE);

        service.clearFailures(PRINCIPAL, SOURCE);

        assertThat(redisTemplate.hasKey(ATTEMPT_KEYS.get(0))).isFalse();
        assertThat(redisTemplate.hasKey(ATTEMPT_KEYS.get(1))).isFalse();
    }

    @Test
    void attemptsFromDifferentSourcesDoNotLockThePrincipal() {
        LoginAttemptService service = service(20, 5);
        service.reserveAttempt(PRINCIPAL, SOURCE);
        service.reserveAttempt(PRINCIPAL, "198.51.100.11");
        service.reserveAttempt(PRINCIPAL, "198.51.100.12");
    }

    private boolean reserveAfterStart(LoginAttemptService service, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.reserveAttempt(PRINCIPAL, SOURCE);
            return true;
        } catch (ResponseStatusException ex) {
            return false;
        }
    }

    private LoginAttemptService service(
            int maxAttemptsPerSource,
            int maxAttemptsPerPrincipalSource
    ) {
        LoginAttemptService service = new LoginAttemptService(redisTemplate);
        ReflectionTestUtils.setField(service, "windowSeconds", 900L);
        ReflectionTestUtils.setField(service, "maxAttemptsPerSource", maxAttemptsPerSource);
        ReflectionTestUtils.setField(service, "maxAttemptsPerPrincipalSource", maxAttemptsPerPrincipalSource);
        return service;
    }
}
