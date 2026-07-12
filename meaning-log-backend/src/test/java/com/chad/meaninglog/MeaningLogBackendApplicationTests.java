package com.chad.meaninglog;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.entity.AiChatMessage;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.repository.AiChatMessageRepository;
import com.chad.meaninglog.repository.AiChatSessionRepository;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.service.MeaningLogLifecycleService;
import com.chad.meaninglog.service.XiaojiChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "jwt.secret=Z3J5bEJ4L2lxanFQU0xJMzVGcFhTc0cwWFFUTzVaWlNkRTY=")
class MeaningLogBackendApplicationTests {

    private static final long TIMEOUT_SECONDS = 5;
    private static final long BLOCK_TIMEOUT_MILLIS = 250;

    private final List<LogChatFixture> fixtures = new ArrayList<>();

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private MeaningLogRepository meaningLogRepository;

    @Autowired
    private MeaningLogLifecycleService meaningLogLifecycleService;

    @Autowired
    private AiChatSessionRepository aiChatSessionRepository;

    @Autowired
    private AiChatMessageRepository aiChatMessageRepository;

    @Autowired
    private XiaojiChatService xiaojiChatService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void mybatisPlusRepositoriesCanPersistAndQueryByUser() {
        UserAccount user = new UserAccount();
        user.setEmail("mybatis-plus-smoke@example.com");
        user.setUsername("mybatisPlusSmoke");
        user.setPasswordHash("test-hash");
        userAccountRepository.save(user);

        MeaningLog log = new MeaningLog();
        log.setUser(user);
        log.setTitle("MyBatis-Plus smoke test");
        log.setContent("Verify insert and query wrapper mapping.");
        log.setLogDate(LocalDate.now());
        meaningLogRepository.save(log);

        assertThat(user.getId()).isNotNull();
        assertThat(log.getId()).isNotNull();
        assertThat(meaningLogRepository.findByIdAndUser(log.getId(), user)).isPresent();
    }

    @Test
    @Transactional
    void deletingLogAlsoDeletesAssociatedChatSessionsAndMessages() {
        UserAccount user = new UserAccount();
        user.setEmail("log-chat-cleanup@example.com");
        user.setUsername("logChatCleanup");
        user.setPasswordHash("test-hash");
        userAccountRepository.save(user);

        MeaningLog log = new MeaningLog();
        log.setUser(user);
        log.setTitle("Log with chat");
        log.setContent("Delete associated chat data.");
        log.setLogDate(LocalDate.of(2026, 7, 12));
        meaningLogRepository.save(log);

        AiChatSession session = new AiChatSession();
        session.setUser(user);
        session.setMeaningLog(log);
        session.setType(AiChatSession.Type.LOG);
        session.setTitle("Log with chat");
        aiChatSessionRepository.save(session);

        AiChatMessage message = new AiChatMessage();
        message.setSession(session);
        message.setRole(AiChatMessage.Role.USER);
        message.setContent("Please refine this log.");
        aiChatMessageRepository.save(message);

        meaningLogLifecycleService.delete(user, log.getId());
        xiaojiChatService.persistStreamReply(session, "Late stream reply.");

        assertThat(meaningLogRepository.selectById(log.getId())).isNull();
        assertThat(aiChatSessionRepository.selectById(session.getId())).isNull();
        assertThat(aiChatMessageRepository.selectById(message.getId())).isNull();
    }

    @Test
    void deleteWaitsForLockedReplyThenDeletesEveryMessage() throws Exception {
        LogChatFixture fixture = createLogChat();
        CountDownLatch replyLocked = new CountDownLatch(1);
        CountDownLatch releaseReply = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch deleteFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> reply = executor.submit(() -> inTransaction(() -> {
                assertThat(meaningLogRepository.findByIdForUpdate(fixture.log().getId())).isPresent();
                replyLocked.countDown();
                await(releaseReply);
                xiaojiChatService.persistStreamReply(fixture.session(), "Reply before delete.");
            }));
            assertThat(replyLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> delete = executor.submit(() -> {
                deleteStarted.countDown();
                inTransaction(() -> meaningLogLifecycleService.delete(fixture.user(), fixture.log().getId()));
                deleteFinished.countDown();
            });
            assertThat(deleteStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(deleteFinished.await(BLOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isFalse();

            releaseReply.countDown();
            reply.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            delete.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertLogChatDeleted(fixture);
        } finally {
            releaseReply.countDown();
            shutdownExecutor(executor);
        }
    }

    @Test
    void replyWaitsForLockedDeleteThenDoesNotCreateMessage() throws Exception {
        LogChatFixture fixture = createLogChat();
        CountDownLatch deleteLocked = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        CountDownLatch replyStarted = new CountDownLatch(1);
        CountDownLatch replyFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> delete = executor.submit(() -> inTransaction(() -> {
                assertThat(meaningLogRepository.findByIdAndUserForUpdate(
                        fixture.log().getId(), fixture.user())).isPresent();
                deleteLocked.countDown();
                await(releaseDelete);
                meaningLogLifecycleService.delete(fixture.user(), fixture.log().getId());
            }));
            assertThat(deleteLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> reply = executor.submit(() -> {
                replyStarted.countDown();
                inTransaction(() -> xiaojiChatService.persistStreamReply(fixture.session(), "Reply after delete."));
                replyFinished.countDown();
            });
            assertThat(replyStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(replyFinished.await(BLOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isFalse();

            releaseDelete.countDown();
            delete.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            reply.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertLogChatDeleted(fixture);
        } finally {
            releaseDelete.countDown();
            shutdownExecutor(executor);
        }
    }

    private LogChatFixture createLogChat() {
        LogChatFixture fixture = transactionTemplate().execute(status -> {
            String suffix = UUID.randomUUID().toString();
            UserAccount user = new UserAccount();
            user.setEmail("lock-" + suffix + "@example.com");
            user.setUsername("lock" + suffix);
            user.setPasswordHash("test-hash");
            userAccountRepository.save(user);

            MeaningLog log = new MeaningLog();
            log.setUser(user);
            log.setTitle("Log with stream chat");
            log.setContent("Verify concurrent stream reply cleanup.");
            log.setLogDate(LocalDate.of(2026, 7, 13));
            meaningLogRepository.save(log);

            AiChatSession session = new AiChatSession();
            session.setUser(user);
            session.setMeaningLog(log);
            session.setType(AiChatSession.Type.LOG);
            session.setTitle("Log with stream chat");
            aiChatSessionRepository.save(session);

            AiChatMessage message = new AiChatMessage();
            message.setSession(session);
            message.setRole(AiChatMessage.Role.USER);
            message.setContent("Please refine this log.");
            aiChatMessageRepository.save(message);
            return new LogChatFixture(user, log, session);
        });
        fixtures.add(fixture);
        return fixture;
    }

    private void assertLogChatDeleted(LogChatFixture fixture) {
        assertThat(meaningLogRepository.selectById(fixture.log().getId())).isNull();
        assertThat(aiChatSessionRepository.selectById(fixture.session().getId())).isNull();
        assertThat(aiChatMessageRepository.findBySessionOrderByCreatedAtAsc(fixture.session())).isEmpty();
    }

    private void inTransaction(Runnable action) {
        transactionTemplate().executeWithoutResult(status -> action.run());
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent transaction", ex);
        }
    }

    @AfterEach
    void cleanUpFixtures() {
        for (LogChatFixture fixture : fixtures) {
            inTransaction(() -> {
                aiChatMessageRepository.deleteByMeaningLogId(fixture.log().getId());
                aiChatSessionRepository.deleteByMeaningLog(fixture.log());
                meaningLogRepository.delete(fixture.log());
                userAccountRepository.deleteById(fixture.user().getId());
            });
        }
        fixtures.clear();
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try {
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while stopping concurrent transactions", ex);
        }
    }

    private record LogChatFixture(UserAccount user, MeaningLog log, AiChatSession session) {
    }
}
