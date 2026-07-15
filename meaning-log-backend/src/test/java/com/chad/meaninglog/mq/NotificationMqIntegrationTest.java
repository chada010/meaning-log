package com.chad.meaninglog.mq;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.service.community.NotificationService;
import com.chad.meaninglog.service.community.NotificationSseManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 验证 NotificationService 从 Redis Pub/Sub 换成 RabbitMQ 后:
 * <ul>
 *   <li>a) 事务未提交/回滚 → MQ 未投递, SseManager 未收到 push (通过 rollback 场景反证)</li>
 *   <li>b) 事务提交后 → 消息经 fanout + AnonymousQueue 到达本机 Listener, SseManager.push 被调</li>
 *   <li>c) Listener 收到消息后正确调用 SseManager (b 的正例覆盖此路径)</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "jwt.secret=Z3J5bEJ4L2lxanFQU0xJMzVGcFhTc0cwWFFUTzVaWlNkRTY=",
        "spring.mail.password=test-smtp-password",
        "mail.from=noreply@example.com"
})
class NotificationMqIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("meaning_log")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management"));

    @DynamicPropertySource
    static void wireInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private MeaningLogRepository meaningLogRepository;

    @Autowired
    private PublicLogRepository publicLogRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    @MockitoSpyBean
    private NotificationSseManager sseManager;

    @Test
    void notifyLikeAfterCommitReachesSseManagerViaFanout() {
        UserAccount actor = createUser("actor-commit");
        UserAccount receiver = createUser("receiver-commit");
        PublicLog publicLog = createPublicLog(receiver);

        new TransactionTemplate(txManager).executeWithoutResult(status ->
                notificationService.notifyLike(actor, receiver.getId(), publicLog.getId()));

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                verify(sseManager, atLeastOnce()).push(eq(receiver.getId()), anyString()));
    }

    @Test
    void notifyLikeInRolledBackTransactionNeverReachesSseManager() throws Exception {
        UserAccount actor = createUser("actor-rollback");
        UserAccount receiver = createUser("receiver-rollback");
        PublicLog publicLog = createPublicLog(receiver);

        try {
            new TransactionTemplate(txManager).executeWithoutResult(status -> {
                notificationService.notifyLike(actor, receiver.getId(), publicLog.getId());
                status.setRollbackOnly();
            });
        } catch (RuntimeException ignore) {
            // 若 TransactionTemplate 在 rollback-only 时抛 UnexpectedRollback,吞掉,断言看后面
        }

        // 消息投递是 afterCommit 触发, 回滚场景永远不该触发. 短暂等待避免竞态误判.
        Thread.sleep(1500);
        verify(sseManager, never()).push(eq(receiver.getId()), anyString());
    }

    private UserAccount createUser(String suffix) {
        UserAccount user = new UserAccount();
        user.setEmail(suffix + "@example.com");
        user.setUsername(suffix);
        user.setPasswordHash("test-hash");
        return userAccountRepository.save(user);
    }

    private PublicLog createPublicLog(UserAccount author) {
        MeaningLog log = new MeaningLog();
        log.setUser(author);
        log.setTitle("通知测试日志");
        log.setContent("通知必须引用真实帖子");
        log.setLogDate(LocalDate.of(2026, 7, 15));
        meaningLogRepository.save(log);

        PublicLog publicLog = new PublicLog();
        publicLog.setLogId(log.getId());
        publicLog.setUserId(author.getId());
        publicLog.setPublishedAt(LocalDateTime.of(2026, 7, 15, 12, 0));
        publicLog.setStatus(PublicLog.Status.VISIBLE.name());
        publicLog.setCacheVersion(1L);
        return publicLogRepository.save(publicLog);
    }
}
