package com.chad.meaninglog.service.community;

import com.chad.meaninglog.dto.community.CommentRequest;
import com.chad.meaninglog.entity.PostComment;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.PostCommentRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityCommentServiceTests {

    private PublicLogRepository publicLogRepository;
    private PostCommentRepository postCommentRepository;
    private UserAccountRepository userAccountRepository;
    private SensitiveWordFilter sensitiveWordFilter;
    private CommunityRedisService redis;
    private HotScoreCalculator hotScoreCalculator;
    private NotificationService notificationService;
    private CommunityCommentService service;

    private UserAccount user;
    private PublicLog publicLog;

    @BeforeEach
    void setUp() {
        publicLogRepository = mock(PublicLogRepository.class);
        postCommentRepository = mock(PostCommentRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        sensitiveWordFilter = mock(SensitiveWordFilter.class);
        redis = mock(CommunityRedisService.class);
        hotScoreCalculator = mock(HotScoreCalculator.class);
        notificationService = mock(NotificationService.class);
        service = new CommunityCommentService(publicLogRepository, postCommentRepository,
                userAccountRepository, sensitiveWordFilter, redis, hotScoreCalculator, notificationService);

        user = new UserAccount();
        user.setId(10L);
        user.setUsername("commenter");

        publicLog = new PublicLog();
        publicLog.setId(100L);
        publicLog.setUserId(20L);
        publicLog.setStatus(PublicLog.Status.VISIBLE.name());
        when(publicLogRepository.findVisibleById(100L)).thenReturn(Optional.of(publicLog));
        when(sensitiveWordFilter.firstHit(any())).thenReturn(null);
    }

    @Test
    void rejectsReplyWhoseParentBelongsToADifferentPublicLog() {
        PostComment foreignParent = new PostComment();
        foreignParent.setId(999L);
        foreignParent.setPublicLogId(200L);
        when(postCommentRepository.selectById(999L)).thenReturn(foreignParent);

        CommentRequest request = new CommentRequest();
        request.setContent("跨帖回复越权");
        request.setParentId(999L);

        assertThatThrownBy(() -> service.create(user, 100L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("父评论不存在或不属于当前帖子");

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository, never()).insert(captor.capture());
        verify(redis, never()).incrComment(any());
        verify(notificationService, never()).notifyComment(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsReplyWhoseParentDoesNotExist() {
        when(postCommentRepository.selectById(888L)).thenReturn(null);

        CommentRequest request = new CommentRequest();
        request.setContent("父评论不存在");
        request.setParentId(888L);

        assertThatThrownBy(() -> service.create(user, 100L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("父评论不存在或不属于当前帖子");

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository, never()).insert(captor.capture());
    }

    @Test
    void acceptsReplyWhoseParentBelongsToTheSamePublicLog() {
        PostComment sameParent = new PostComment();
        sameParent.setId(500L);
        sameParent.setPublicLogId(100L);
        when(postCommentRepository.selectById(500L)).thenReturn(sameParent);
        when(redis.incrComment(100L)).thenReturn(1L);
        when(redis.getCount(any())).thenReturn(0L);
        when(hotScoreCalculator.score(anyLong(), anyLong(), anyLong(), any())).thenReturn(0.0);

        CommentRequest request = new CommentRequest();
        request.setContent("同帖回复");
        request.setParentId(500L);

        service.create(user, 100L, request);

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).insert(captor.capture());
        verify(redis).incrComment(100L);
    }

    @Test
    void acceptsTopLevelCommentWithoutParent() {
        when(redis.incrComment(100L)).thenReturn(1L);
        when(redis.getCount(any())).thenReturn(0L);
        when(hotScoreCalculator.score(anyLong(), anyLong(), anyLong(), any())).thenReturn(0.0);

        CommentRequest request = new CommentRequest();
        request.setContent("一级评论");

        service.create(user, 100L, request);

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).insert(captor.capture());
        verify(postCommentRepository, never()).selectById(any());
    }
}
