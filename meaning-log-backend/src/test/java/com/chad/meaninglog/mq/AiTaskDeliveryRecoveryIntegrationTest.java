package com.chad.meaninglog.mq;

import com.chad.meaninglog.client.AiUnavailableException;
import com.chad.meaninglog.entity.AiTask;
import com.chad.meaninglog.entity.AiTaskStatus;
import com.chad.meaninglog.entity.AiTaskType;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.mq.listener.AiTaskDlqListener;
import com.chad.meaninglog.mq.listener.AiTaskExecutor;
import com.chad.meaninglog.mq.producer.AiTaskProducer;
import com.chad.meaninglog.repository.AiTaskRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.service.AiTaskDeliveryService;
import com.chad.meaninglog.service.AiTaskNotifier;
import com.chad.meaninglog.service.AiTaskReaper;
import com.chad.meaninglog.service.AiTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.mail.password=test-smtp-password",
        "mail.from=noreply@example.com",
        "jwt.secret=Z3J5bEJ4L2lxanFQU0xJMzVGcFhTc0cwWFFUTzVaWlNkRTY=",
        "app.ai.api-key=test-key",
        "spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "ai.task.reaper.enabled=false"
})
class AiTaskDeliveryRecoveryIntegrationTest {

    private static final long TIMEOUT_SECONDS = 5;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("meaning_log")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void wireDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private AiTaskRepository aiTaskRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private AiTaskService aiTaskService;
    @Autowired private AiTaskDeliveryService aiTaskDeliveryService;
    @Autowired private AiTaskExecutor aiTaskExecutor;
    @Autowired private AiTaskDlqListener aiTaskDlqListener;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Clock businessClock;

    @MockitoSpyBean private AiTaskProducer aiTaskProducer;
    @MockitoBean private AiTaskNotifier aiTaskNotifier;
    @MockitoBean private RedisMessageListenerContainer redisMessageListenerContainer;

    @AfterEach
    void cleanDatabase() {
        aiTaskRepository.delete(null);
        userAccountRepository.delete(null);
    }

    @Test
    void immediateMqFailureStillCommitsPendingTask() {
        doThrow(new AmqpException("rabbit unavailable")).when(aiTaskProducer).send(any());

        AiTask created = aiTaskService.create(
                null, AiTaskType.CHAT, new AiTaskInputs.ChatInput(null, "hello"));

        AiTask saved = aiTaskRepository.selectById(created.getId());
        assertThat(saved.getStatus()).isEqualTo(AiTaskStatus.PENDING);
        assertThat(saved.getPublishAttempts()).isEqualTo(1);
        assertThat(saved.getLastPublishAt()).isNotNull();
    }

    @Test
    void recoveryScanRepublishesOnlyConfiguredStaleBatch() throws Exception {
        LocalDateTime staleAt = LocalDateTime.now(businessClock).minusMinutes(5);
        AiTask first = createTask(AiTaskStatus.PENDING, null, staleAt);
        AiTask second = createTask(AiTaskStatus.PENDING, null, staleAt);
        AiTask overflow = createTask(AiTaskStatus.PENDING, null, staleAt);
        AiTask recent = createTask(AiTaskStatus.PENDING, null, LocalDateTime.now(businessClock));
        doNothing().when(aiTaskProducer).send(any());
        AiTaskReaper reaper = new AiTaskReaper(
                aiTaskRepository, aiTaskDeliveryService, aiTaskNotifier, businessClock);
        ReflectionTestUtils.setField(reaper, "pendingStaleSeconds", 60L);
        ReflectionTestUtils.setField(reaper, "deliveryBatchSize", 2);

        reaper.recoverStalePending();

        assertThat(aiTaskRepository.selectById(first.getId()).getPublishAttempts()).isEqualTo(1);
        assertThat(aiTaskRepository.selectById(second.getId()).getPublishAttempts()).isEqualTo(1);
        assertThat(aiTaskRepository.selectById(overflow.getId()).getPublishAttempts()).isZero();
        assertThat(aiTaskRepository.selectById(recent.getId()).getPublishAttempts()).isZero();
        verify(aiTaskProducer, times(2)).send(any());
    }

    @Test
    void concurrentDuplicateMessagesExecuteWorkOnlyOnce() throws Exception {
        AiTask task = createTask(AiTaskStatus.PENDING, null, LocalDateTime.now(businessClock));
        AiTaskMessage message = new AiTaskMessage(task.getId(), task.getTaskType());
        AtomicInteger workCalls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch releaseWork = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = pool.submit(() -> executeAfter(start, message, workCalls, workStarted, releaseWork));
            Future<?> second = pool.submit(() -> executeAfter(start, message, workCalls, workStarted, releaseWork));
            start.countDown();
            assertThat(workStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .until(() -> first.isDone() || second.isDone());
            assertThat(workCalls).hasValue(1);

            releaseWork.countDown();
            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(aiTaskRepository.selectById(task.getId()).getStatus()).isEqualTo(AiTaskStatus.SUCCESS);
        } finally {
            releaseWork.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void retryableFailureReturnsToPendingAndNextDeliverySucceeds() throws Exception {
        AiTask task = createTask(AiTaskStatus.PENDING, null, LocalDateTime.now(businessClock));
        AiTaskMessage message = new AiTaskMessage(task.getId(), task.getTaskType());

        assertThatThrownBy(() -> aiTaskExecutor.execute(message, AiTaskInputs.ChatInput.class,
                (user, input) -> {
                    throw new IllegalStateException("temporary failure");
                })).isInstanceOf(IllegalStateException.class);

        AiTask retryable = aiTaskRepository.selectById(task.getId());
        assertThat(retryable.getStatus()).isEqualTo(AiTaskStatus.PENDING);
        assertThat(retryable.getRetryCount()).isEqualTo(1);
        aiTaskExecutor.execute(message, AiTaskInputs.ChatInput.class, (user, input) -> "recovered");
        AiTask succeeded = aiTaskRepository.selectById(task.getId());
        assertThat(succeeded.getStatus()).isEqualTo(AiTaskStatus.SUCCESS);
        assertThat(succeeded.getResultJson()).isEqualTo("\"recovered\"");
    }

    @Test
    void nonRetryableAndTerminalDuplicateMessagesDoNotRunAgain() throws Exception {
        AiTask unavailable = createTask(
                AiTaskStatus.PENDING, null, LocalDateTime.now(businessClock));
        AiTaskMessage unavailableMessage = new AiTaskMessage(unavailable.getId(), unavailable.getTaskType());
        aiTaskExecutor.execute(unavailableMessage, AiTaskInputs.ChatInput.class,
                (user, input) -> {
                    throw new AiUnavailableException("provider down");
                });
        assertThat(aiTaskRepository.selectById(unavailable.getId()).getStatus()).isEqualTo(AiTaskStatus.FAILED);

        AiTask success = createTask(AiTaskStatus.SUCCESS, null, LocalDateTime.now(businessClock));
        AiTask failed = createTask(AiTaskStatus.FAILED, null, LocalDateTime.now(businessClock));
        AtomicInteger workCalls = new AtomicInteger();
        aiTaskExecutor.execute(message(success), AiTaskInputs.ChatInput.class,
                (user, input) -> workCalls.incrementAndGet());
        aiTaskExecutor.execute(message(failed), AiTaskInputs.ChatInput.class,
                (user, input) -> workCalls.incrementAndGet());
        aiTaskDlqListener.handle(message(success));

        assertThat(workCalls).hasValue(0);
        assertThat(aiTaskRepository.selectById(success.getId()).getStatus()).isEqualTo(AiTaskStatus.SUCCESS);
        assertThat(aiTaskRepository.selectById(failed.getId()).getStatus()).isEqualTo(AiTaskStatus.FAILED);
        verify(aiTaskNotifier, never()).publishDone(success.getId());
    }

    @Test
    void staleRunningBecomesFailedAndNotifiesWaiters() throws Exception {
        AiTask task = createTask(
                AiTaskStatus.RUNNING, null, LocalDateTime.now(businessClock).minusMinutes(20));
        AiTaskReaper reaper = new AiTaskReaper(
                aiTaskRepository, aiTaskDeliveryService, aiTaskNotifier, businessClock);
        ReflectionTestUtils.setField(reaper, "runningTimeoutSeconds", 60L);
        ReflectionTestUtils.setField(reaper, "reaperBatchSize", 10);

        reaper.reapStaleRunning();

        AiTask failed = aiTaskRepository.selectById(task.getId());
        assertThat(failed.getStatus()).isEqualTo(AiTaskStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("running timeout");
        verify(aiTaskNotifier).publishDone(task.getId());
    }

    @Test
    void taskOwnershipCheckRemainsUnchanged() throws Exception {
        UserAccount owner = createUser("owner");
        UserAccount other = createUser("other");
        AiTask task = createTask(
                AiTaskStatus.PENDING, owner.getId(), LocalDateTime.now(businessClock));

        assertThat(aiTaskService.findByIdForUser(owner, task.getId()).id()).isEqualTo(task.getId());
        assertThatThrownBy(() -> aiTaskService.findByIdForUser(other, task.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    private void executeAfter(
            CountDownLatch start,
            AiTaskMessage message,
            AtomicInteger workCalls,
            CountDownLatch workStarted,
            CountDownLatch releaseWork
    ) {
        awaitLatch(start);
        aiTaskExecutor.execute(message, AiTaskInputs.ChatInput.class, (user, input) -> {
            workCalls.incrementAndGet();
            workStarted.countDown();
            awaitLatch(releaseWork);
            return "done";
        });
    }

    private AiTask createTask(AiTaskStatus status, Long userId, LocalDateTime updatedAt) throws Exception {
        AiTask task = new AiTask();
        task.setUserId(userId);
        task.setTaskType(AiTaskType.CHAT);
        task.setStatus(status);
        task.setInputJson(objectMapper.writeValueAsString(new AiTaskInputs.ChatInput(null, "hello")));
        task.setRetryCount(0);
        task.setPublishAttempts(0);
        task.setCreatedAt(updatedAt);
        task.setUpdatedAt(updatedAt);
        aiTaskRepository.insert(task);
        return task;
    }

    private UserAccount createUser(String name) {
        UserAccount user = new UserAccount();
        user.setEmail(name + "@example.com");
        user.setUsername(name);
        user.setPasswordHash("test-hash");
        return userAccountRepository.save(user);
    }

    private static AiTaskMessage message(AiTask task) {
        return new AiTaskMessage(task.getId(), task.getTaskType());
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent AI task test");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for AI task test", ex);
        }
    }
}
