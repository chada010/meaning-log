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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "jwt.secret=Z3J5bEJ4L2lxanFQU0xJMzVGcFhTc0cwWFFUTzVaWlNkRTY=")
class MeaningLogBackendApplicationTests {

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
}
