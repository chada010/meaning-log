package com.chad.meaninglog.service.community;

import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityLikeServiceTests {

    private PublicLogRepository publicLogRepository;
    private PostLikeRepository postLikeRepository;
    private CommunityRedisRepairService repairService;
    private NotificationService notificationService;
    private CommunityLikeService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        publicLogRepository = mock(PublicLogRepository.class);
        postLikeRepository = mock(PostLikeRepository.class);
        repairService = mock(CommunityRedisRepairService.class);
        notificationService = mock(NotificationService.class);
        service = new CommunityLikeService(publicLogRepository, postLikeRepository,
                repairService, notificationService,
                Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneId.of("Asia/Shanghai")));

        PublicLog publicLog = new PublicLog();
        publicLog.setId(100L);
        publicLog.setUserId(20L);
        when(publicLogRepository.findVisibleByIdForUpdate(100L)).thenReturn(Optional.of(publicLog));
        when(postLikeRepository.countByPublicLogId(100L)).thenReturn(1L);

        user = new UserAccount();
        user.setId(10L);
        user.setUsername("liker");
    }

    @Test
    void duplicateLikeDoesNotIncrementOrNotify() {
        when(postLikeRepository.insertIfAbsent(eq(100L), eq(10L), any())).thenReturn(0);

        CommunityLikeService.LikeResult result = service.like(user, 100L);

        assertThat(result.likeCount()).isEqualTo(1L);
        verify(publicLogRepository, never()).incrementLikeCount(any(), any());
        verify(notificationService, never()).notifyLike(any(), any(), any());
        verify(repairService).enqueuePostState(100L, 10L);
    }

    @Test
    void databaseFailureDoesNotScheduleRedisRepair() {
        when(postLikeRepository.insertIfAbsent(eq(100L), eq(10L), any())).thenReturn(1);
        when(publicLogRepository.incrementLikeCount(eq(100L), any()))
                .thenThrow(new DataAccessResourceFailureException("db unavailable"));

        assertThatThrownBy(() -> service.like(user, 100L))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(repairService, never()).enqueuePostState(any(), any());
        verify(notificationService, never()).notifyLike(any(), any(), any());
    }
}
