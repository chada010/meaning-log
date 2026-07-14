package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.PostLike;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.service.community.job.CounterFlushJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIf(value = "com.chad.meaninglog.service.community.CommunityLikeIntegrationTest#dockerAvailable",
        disabledReason = "需要 Docker 环境")
@SpringBootTest(properties = {
        "spring.mail.password=test-smtp-password",
        "mail.from=noreply@example.com",
        "jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
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
    @Autowired private CommunityLikeService likeService;
    @Autowired private CommunityRedisService redis;
    @Autowired private CounterFlushJob counterFlushJob;
    @Autowired private StringRedisTemplate redisTemplate;

    private UserAccount author;
    private UserAccount liker;
    private PublicLog publicLog;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        author = createUser("author");
        liker = createUser("liker");
        MeaningLog log = createLog(author);
        publicLog = new PublicLog();
        publicLog.setLogId(log.getId());
        publicLog.setUserId(author.getId());
        publicLog.setPublishedAt(LocalDateTime.now());
        publicLog.setStatus(PublicLog.Status.VISIBLE.name());
        publicLogRepository.save(publicLog);
        redis.seedCounts(publicLog.getId(), 0, 0, 0);
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
    }

    @Test
    void duplicateLike_isIdempotent() {
        likeService.like(liker, publicLog.getId());
        CommunityLikeService.LikeResult second = likeService.like(liker, publicLog.getId());

        assertThat(second.liked()).isTrue();
        assertThat(second.likeCount()).isEqualTo(1L);
        assertThat(postLikeRepository.countByPublicLogId(publicLog.getId())).isEqualTo(1L);
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
        likeService.like(liker, publicLog.getId());
        counterFlushJob.flush();

        PublicLog reloaded = publicLogRepository.selectById(publicLog.getId());
        assertThat(reloaded.getLikeCount()).isEqualTo(1L);
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
