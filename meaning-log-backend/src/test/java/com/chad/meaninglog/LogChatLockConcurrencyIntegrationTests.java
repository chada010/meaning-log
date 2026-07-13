package com.chad.meaninglog;

import com.chad.meaninglog.dto.LogAiResult;
import com.chad.meaninglog.entity.AiChatMessage;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.AiChatMessageRepository;
import com.chad.meaninglog.repository.AiChatSessionRepository;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.service.AiService;
import com.chad.meaninglog.service.MeaningLogLifecycleService;
import com.chad.meaninglog.service.MeaningLogSupportService;
import com.chad.meaninglog.service.XiaojiChatService;
import com.chad.meaninglog.service.XiaojiChatSupportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.mail.password=test-smtp-password",
        "mail.from=noreply@example.com"
})
class LogChatLockConcurrencyIntegrationTests {

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

    @MockitoBean
    private AiService aiService;

    @MockitoSpyBean
    private XiaojiChatSupportService xiaojiChatSupportService;

    @MockitoSpyBean
    private MeaningLogSupportService meaningLogSupportService;

    @Test
    void streamReplyAcquiresProductionLockBeforeDeleteCanProceed() throws Exception {
        LogChatFixture fixture = createLogChat();
        CountDownLatch replyLocked = new CountDownLatch(1);
        CountDownLatch releaseReply = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch deleteFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        doAnswer(invocation -> {
            boolean locked = (boolean) invocation.callRealMethod();
            replyLocked.countDown();
            await(releaseReply);
            return locked;
        }).when(xiaojiChatSupportService).lockLogForStreamReply(fixture.log().getId());

        try {
            Future<?> reply = executor.submit(
                    () -> xiaojiChatService.persistStreamReply(fixture.session(), "Reply before delete.")
            );
            assertThat(replyLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> delete = executor.submit(() -> {
                deleteStarted.countDown();
                meaningLogLifecycleService.delete(fixture.user(), fixture.log().getId());
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
    void deleteAcquiresProductionLockBeforeStreamReplyCanProceed() throws Exception {
        LogChatFixture fixture = createLogChat();
        CountDownLatch deleteLocked = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        CountDownLatch replyFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        doAnswer(invocation -> {
            MeaningLog log = (MeaningLog) invocation.callRealMethod();
            deleteLocked.countDown();
            await(releaseDelete);
            return log;
        }).when(meaningLogSupportService).getMeaningLogForUpdate(fixture.user(), fixture.log().getId());

        try {
            Future<?> delete = executor.submit(
                    () -> meaningLogLifecycleService.delete(fixture.user(), fixture.log().getId())
            );
            assertThat(deleteLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> reply = executor.submit(() -> {
                xiaojiChatService.persistStreamReply(fixture.session(), "Reply after delete.");
                replyFinished.countDown();
            });
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

    @Test
    void logChatHoldsProductionLockUntilAssistantReplyIsPersisted() throws Exception {
        LogChatFixture fixture = createLogChat();
        CountDownLatch aiStarted = new CountDownLatch(1);
        CountDownLatch releaseAi = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch deleteFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        doAnswer(invocation -> {
            aiStarted.countDown();
            await(releaseAi);
            return new LogAiResult("Refined title", "Refined summary", List.of("focus"));
        }).when(aiService).refineLogSummary(any(MeaningLog.class), anyList(), anyList(), anyString());

        try {
            Future<?> chat = executor.submit(
                    () -> xiaojiChatService.chatWithLog(fixture.user(), fixture.log().getId(), "Please refine this log.")
            );
            assertThat(aiStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> delete = executor.submit(() -> {
                deleteStarted.countDown();
                meaningLogLifecycleService.delete(fixture.user(), fixture.log().getId());
                deleteFinished.countDown();
            });
            assertThat(deleteStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(deleteFinished.await(BLOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isFalse();

            releaseAi.countDown();
            chat.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            delete.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertLogChatDeleted(fixture);
        } finally {
            releaseAi.countDown();
            shutdownExecutor(executor);
        }
    }

    @Test
    void streamPreparationAcquiresProductionLockBeforeDeleteCanProceed() throws Exception {
        LogChatFixture fixture = createLogChat();
        CountDownLatch streamLocked = new CountDownLatch(1);
        CountDownLatch releaseStream = new CountDownLatch(1);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch deleteFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        doAnswer(invocation -> {
            MeaningLog log = (MeaningLog) invocation.callRealMethod();
            streamLocked.countDown();
            await(releaseStream);
            return log;
        }).when(xiaojiChatSupportService).getMeaningLogForUpdate(fixture.user(), fixture.log().getId());

        try {
            Future<?> stream = executor.submit(
                    () -> xiaojiChatService.prepareLogRefineStream(
                            fixture.user(), fixture.log().getId(), "Prepare stream reply."
                    )
            );
            assertThat(streamLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> delete = executor.submit(() -> {
                deleteStarted.countDown();
                meaningLogLifecycleService.delete(fixture.user(), fixture.log().getId());
                deleteFinished.countDown();
            });
            assertThat(deleteStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(deleteFinished.await(BLOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isFalse();

            releaseStream.countDown();
            stream.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            delete.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertLogChatDeleted(fixture);
        } finally {
            releaseStream.countDown();
            shutdownExecutor(executor);
        }
    }

    private LogChatFixture createLogChat() {
        String suffix = UUID.randomUUID().toString();
        UserAccount user = new UserAccount();
        user.setEmail("lock-" + suffix + "@example.com");
        user.setUsername("lock" + suffix);
        user.setPasswordHash("test-hash");
        userAccountRepository.save(user);

        MeaningLog log = new MeaningLog();
        log.setUser(user);
        log.setTitle("Log with chat");
        log.setContent("Verify concurrent chat cleanup.");
        log.setLogDate(LocalDate.of(2026, 7, 13));
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

        LogChatFixture fixture = new LogChatFixture(user, log, session);
        fixtures.add(fixture);
        return fixture;
    }

    private void assertLogChatDeleted(LogChatFixture fixture) {
        assertThat(meaningLogRepository.selectById(fixture.log().getId())).isNull();
        assertThat(aiChatSessionRepository.selectById(fixture.session().getId())).isNull();
        assertThat(aiChatMessageRepository.findBySessionOrderByCreatedAtAsc(fixture.session())).isEmpty();
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
            aiChatMessageRepository.deleteByMeaningLogId(fixture.log().getId());
            aiChatSessionRepository.deleteByMeaningLog(fixture.log());
            meaningLogRepository.delete(fixture.log());
            userAccountRepository.deleteById(fixture.user().getId());
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
