package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.PostComment;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.Notification;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.mq.producer.NotificationProducer;
import com.chad.meaninglog.repository.CommunityRedisRepairRepository;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.NotificationRepository;
import com.chad.meaninglog.repository.PostCommentRepository;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.repository.UserFollowRepository;
import com.chad.meaninglog.service.MeaningLogLifecycleService;
import com.chad.meaninglog.service.community.job.CounterFlushJob;
import com.chad.meaninglog.service.community.job.ReconcileJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIf(value = "com.chad.meaninglog.service.community.CommunityLikeIntegrationTest#dockerAvailable",
        disabledReason = "需要 Docker 环境")
@SpringBootTest(properties = {
        "spring.mail.password=test-smtp-password",
        "mail.from=noreply@example.com",
        "spring.task.scheduling.enabled=false",
        "jwt.secret=Z3J5bEJ4L2lxanFQU0xJMzVGcFhTc0cwWFFUTzVaWlNkRTY="
})
class CommunityLikeIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("meaning_log")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    static boolean dockerAvailable() {
        try {
            org.testcontainers.DockerClientFactory.instance().client();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private MeaningLogRepository meaningLogRepository;
    @Autowired private PublicLogRepository publicLogRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private PostCommentRepository postCommentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserFollowRepository userFollowRepository;
    @Autowired private CommunityRedisRepairRepository repairRepository;
    @Autowired private CommunityLikeService likeService;
    @Autowired private CommunityFeedService feedService;
    @Autowired private CommunityRedisRepairService repairService;
    @Autowired private MeaningLogLifecycleService meaningLogLifecycleService;
    @Autowired private CommunityRedisService redis;
    @Autowired private CounterFlushJob counterFlushJob;
    @Autowired private ReconcileJob reconcileJob;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private NotificationProducer notificationProducer;
    @MockitoSpyBean private CommunityRedisRebuilder rebuilder;

    private UserAccount author;
    private UserAccount liker;
    private MeaningLog meaningLog;
    private PublicLog publicLog;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        author = createUser("author");
        liker = createUser("liker");
        meaningLog = createLog(author);
        publicLog = new PublicLog();
        publicLog.setLogId(meaningLog.getId());
        publicLog.setUserId(author.getId());
        publicLog.setPublishedAt(LocalDateTime.now());
        publicLog.setStatus(PublicLog.Status.VISIBLE.name());
        publicLogRepository.save(publicLog);
        redisTemplate.opsForValue().set(CommunityRedisKeys.countLike(publicLog.getId()), "0");
        redisTemplate.opsForValue().set(CommunityRedisKeys.countComment(publicLog.getId()), "0");
        redisTemplate.opsForValue().set(CommunityRedisKeys.countView(publicLog.getId()), "0");
    }

    @Test
    void like_updatesBitmap_counterAndHotZSet() {
        CommunityLikeService.LikeResult result = likeService.like(liker, publicLog.getId());

        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1L);
        assertThat(redis.hasLiked(publicLog.getId(), liker.getId())).isTrue();
        assertThat(redis.getCount(CommunityRedisKeys.countLike(publicLog.getId()))).isEqualTo(1L);
        assertThat(redis.topHot(0, 10)).contains(publicLog.getId());
        assertThat(postLikeRepository.findByPublicLogIdAndUserId(publicLog.getId(), liker.getId())).isPresent();
        assertThat(repairRepository.selectCount(null)).isZero();
    }

    @Test
    void duplicateLike_isIdempotent() {
        likeService.like(liker, publicLog.getId());
        CommunityLikeService.LikeResult second = likeService.like(liker, publicLog.getId());

        assertThat(second.liked()).isTrue();
        assertThat(second.likeCount()).isEqualTo(1L);
        assertThat(postLikeRepository.countByPublicLogId(publicLog.getId())).isEqualTo(1L);
        assertThat(notificationRepository.countByPublicLogIdAndType(
                publicLog.getId(), Notification.Type.LIKE)).isEqualTo(1L);
    }

    @Test
    void concurrentDuplicateLike_changesDatabaseAndNotificationOnlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<CommunityLikeService.LikeResult> first = executor.submit(() -> {
                start.await();
                return likeService.like(liker, publicLog.getId());
            });
            Future<CommunityLikeService.LikeResult> second = executor.submit(() -> {
                start.await();
                return likeService.like(liker, publicLog.getId());
            });
            start.countDown();
            assertThat(first.get().liked()).isTrue();
            assertThat(second.get().liked()).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(postLikeRepository.countByPublicLogId(publicLog.getId())).isEqualTo(1L);
        assertThat(publicLogRepository.selectById(publicLog.getId()).getLikeCount()).isEqualTo(1L);
        assertThat(notificationRepository.countByPublicLogIdAndType(
                publicLog.getId(), Notification.Type.LIKE)).isEqualTo(1L);
    }

    @Test
    void rolledBackDatabaseTransaction_doesNotChangeRedis() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            likeService.like(liker, publicLog.getId());
            status.setRollbackOnly();
        });

        assertThat(postLikeRepository.countByPublicLogId(publicLog.getId())).isZero();
        assertThat(redis.hasLiked(publicLog.getId(), liker.getId())).isFalse();
        assertThat(redis.getCount(CommunityRedisKeys.countLike(publicLog.getId()))).isZero();
    }

    @Test
    void committedDatabaseWithRedisFailure_isRecoveredFromRepairQueue() {
        AtomicBoolean failOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failOnce.getAndSet(false)) {
                throw new RedisConnectionFailureException("test failure");
            }
            return invocation.callRealMethod();
        }).when(rebuilder).rebuild(any());

        likeService.like(liker, publicLog.getId());

        assertThat(postLikeRepository.countByPublicLogId(publicLog.getId())).isEqualTo(1L);
        assertThat(repairRepository.selectCount(null)).isEqualTo(1L);
        assertThat(redis.hasLiked(publicLog.getId(), liker.getId())).isFalse();

        repairService.processPending(10);

        assertThat(repairRepository.selectCount(null)).isZero();
        assertThat(redis.hasLiked(publicLog.getId(), liker.getId())).isTrue();
        assertThat(redis.getCount(CommunityRedisKeys.countLike(publicLog.getId()))).isEqualTo(1L);
    }

    @Test
    void unlike_clearsBitmapAndDecrementsCounter() {
        likeService.like(liker, publicLog.getId());
        CommunityLikeService.LikeResult result = likeService.unlike(liker, publicLog.getId());

        assertThat(result.liked()).isFalse();
        assertThat(result.likeCount()).isEqualTo(0L);
        assertThat(redis.hasLiked(publicLog.getId(), liker.getId())).isFalse();
        assertThat(postLikeRepository.findByPublicLogIdAndUserId(publicLog.getId(), liker.getId())).isEmpty();
    }

    @Test
    void counterFlush_syncsRedisToMysql() {
        long views = redis.recordView(publicLog.getId(), liker.getId(),
                LocalDate.now(ZoneId.of("Asia/Shanghai")), 0L);
        counterFlushJob.flush();

        PublicLog reloaded = publicLogRepository.selectById(publicLog.getId());
        assertThat(reloaded.getViewCount()).isEqualTo(views);
    }

    @Test
    void reconcileRestoresDeletedRecentPostKeys() {
        likeService.like(liker, publicLog.getId());
        redisTemplate.delete(List.of(
                CommunityRedisKeys.likeBitmap(publicLog.getId()),
                CommunityRedisKeys.countLike(publicLog.getId()),
                CommunityRedisKeys.countComment(publicLog.getId()),
                CommunityRedisKeys.countView(publicLog.getId()),
                CommunityRedisKeys.HOT_GLOBAL
        ));

        reconcileJob.reconcile();

        assertThat(redis.hasLiked(publicLog.getId(), liker.getId())).isTrue();
        assertThat(redis.getCount(CommunityRedisKeys.countLike(publicLog.getId()))).isEqualTo(1L);
        assertThat(redis.topHot(0, 10)).contains(publicLog.getId());
    }

    @Test
    void deletingPublishedLog_removesDatabaseRelationsAndPublicAccess() {
        likeService.like(liker, publicLog.getId());
        PostComment comment = new PostComment();
        comment.setPublicLogId(publicLog.getId());
        comment.setUserId(liker.getId());
        comment.setContent("即将随帖子删除");
        postCommentRepository.insert(comment);
        userFollowRepository.insertIfAbsent(liker.getId(), author.getId(), LocalDateTime.now());
        redis.pushToFollowerFeeds(List.of(liker.getId()), publicLog.getId(),
                publicLog.getPublishedAt().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());

        meaningLogLifecycleService.delete(author, meaningLog.getId());

        assertThat(publicLogRepository.selectById(publicLog.getId())).isNull();
        assertThat(postLikeRepository.countByPublicLogId(publicLog.getId())).isZero();
        assertThat(postCommentRepository.countByPublicLogId(publicLog.getId())).isZero();
        assertThat(notificationRepository.countByPublicLogIdAndType(
                publicLog.getId(), Notification.Type.LIKE)).isZero();
        assertThat(redis.topHot(0, 10)).doesNotContain(publicLog.getId());
        assertThat(feedService.loadFeed(liker, CommunityFeedService.FeedType.FOLLOWING, 1, 20))
                .isEmpty();
        assertThatThrownBy(() -> feedService.loadDetail(liker, publicLog.getId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("帖子不存在或已下架");
    }

    private UserAccount createUser(String suffix) {
        UserAccount user = new UserAccount();
        String tag = suffix + "-" + UUID.randomUUID().toString().substring(0, 6);
        user.setEmail(tag + "@test.com");
        user.setUsername(tag);
        user.setPasswordHash("stub-hash");
        return userAccountRepository.save(user);
    }

    private MeaningLog createLog(UserAccount user) {
        MeaningLog log = new MeaningLog();
        log.setTitle("测试日志");
        log.setContent("集成测试正文");
        log.setLogDate(LocalDate.now());
        log.setUserId(user.getId());
        return meaningLogRepository.save(log);
    }
}
