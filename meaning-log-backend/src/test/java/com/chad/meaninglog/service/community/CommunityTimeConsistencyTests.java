package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.repository.UserFollowRepository;
import com.chad.meaninglog.time.BusinessTime;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityTimeConsistencyTests {

    private static final Instant SHANGHAI_AFTER_MIDNIGHT = Instant.parse("2026-07-14T16:30:00Z");

    @Test
    void hotDecayUsesShanghaiClockEvenWhenJvmDefaultIsUtc() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            Clock clock = Clock.fixed(Instant.parse("2026-07-14T17:30:00Z"), BusinessTime.ZONE_ID);
            HotScoreCalculator calculator = new HotScoreCalculator(clock);

            double score = calculator.score(1L, 0L, 0L,
                    LocalDateTime.of(2026, 7, 15, 0, 30));

            assertThat(score).isEqualTo(1.5);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void uvDateUsesShanghaiBusinessDayAtUtcBoundary() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            PublicLogRepository publicLogRepository = mock(PublicLogRepository.class);
            MeaningLogRepository meaningLogRepository = mock(MeaningLogRepository.class);
            UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
            PostLikeRepository postLikeRepository = mock(PostLikeRepository.class);
            UserFollowRepository userFollowRepository = mock(UserFollowRepository.class);
            CommunityRedisService redis = mock(CommunityRedisService.class);

            PublicLog post = post();
            MeaningLog log = new MeaningLog();
            log.setId(200L);
            UserAccount author = user(20L);
            UserAccount viewer = user(10L);
            when(publicLogRepository.findVisibleById(100L)).thenReturn(Optional.of(post));
            when(meaningLogRepository.selectById(200L)).thenReturn(log);
            when(userAccountRepository.selectById(20L)).thenReturn(author);
            when(redis.recordView(eq(100L), eq(10L), any(), eq(5L))).thenReturn(5L);

            CommunityFeedService service = new CommunityFeedService(
                    publicLogRepository, meaningLogRepository, userAccountRepository,
                    postLikeRepository, userFollowRepository, redis,
                    mock(CommunityRedisBatchService.class),
                    Clock.fixed(SHANGHAI_AFTER_MIDNIGHT, BusinessTime.ZONE_ID));

            service.loadDetail(viewer, 100L);

            verify(redis).recordView(100L, 10L, LocalDate.of(2026, 7, 15), 5L);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void publishTimestampUsesInjectedShanghaiClock() {
        PublicLogRepository publicLogRepository = mock(PublicLogRepository.class);
        MeaningLogRepository meaningLogRepository = mock(MeaningLogRepository.class);
        CommunityRedisRepairService repairService = mock(CommunityRedisRepairService.class);
        UserAccount user = user(10L);
        MeaningLog log = new MeaningLog();
        log.setId(200L);
        when(meaningLogRepository.findByIdAndUserForUpdate(200L, user)).thenReturn(Optional.of(log));
        when(publicLogRepository.findByLogIdForUpdate(200L)).thenReturn(Optional.empty());
        when(publicLogRepository.save(any())).thenAnswer(invocation -> {
            PublicLog saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        CommunityPublishService service = new CommunityPublishService(
                publicLogRepository, meaningLogRepository, repairService,
                Clock.fixed(SHANGHAI_AFTER_MIDNIGHT, BusinessTime.ZONE_ID));

        PublicLog published = service.publish(user, 200L);

        assertThat(published.getPublishedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 15, 0, 30));
        verify(repairService).enqueuePostPublish(100L);
    }

    private PublicLog post() {
        PublicLog post = new PublicLog();
        post.setId(100L);
        post.setLogId(200L);
        post.setUserId(20L);
        post.setViewCount(5L);
        post.setStatus(PublicLog.Status.VISIBLE.name());
        return post;
    }

    private UserAccount user(Long id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername("user-" + id);
        return user;
    }
}
