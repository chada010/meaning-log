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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityCommentServiceTests {

    private PublicLogRepository publicLogRepository;
    private PostCommentRepository postCommentRepository;
    private UserAccountRepository userAccountRepository;
    private SensitiveWordFilter sensitiveWordFilter;
    private CommunityRedisRepairService repairService;
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
        repairService = mock(CommunityRedisRepairService.class);
        notificationService = mock(NotificationService.class);
        service = new CommunityCommentService(publicLogRepository, postCommentRepository,
                userAccountRepository, sensitiveWordFilter, repairService, notificationService,
                Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneId.of("Asia/Shanghai")));

        user = new UserAccount();
        user.setId(10L);
        user.setUsername("commenter");

        publicLog = new PublicLog();
        publicLog.setId(100L);
        publicLog.setUserId(20L);
        publicLog.setStatus(PublicLog.Status.VISIBLE.name());
        when(publicLogRepository.findVisibleByIdForUpdate(100L)).thenReturn(Optional.of(publicLog));
        when(publicLogRepository.incrementCommentCount(anyLong(), any())).thenReturn(1);
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
        verify(repairService, never()).enqueuePostState(any(), any());
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
        CommentRequest request = new CommentRequest();
        request.setContent("同帖回复");
        request.setParentId(500L);

        service.create(user, 100L, request);

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).insert(captor.capture());
        verify(publicLogRepository).incrementCommentCount(eq(100L), any());
        verify(repairService).enqueuePostState(100L, null);
    }

    @Test
    void acceptsTopLevelCommentWithoutParent() {
        CommentRequest request = new CommentRequest();
        request.setContent("一级评论");

        service.create(user, 100L, request);

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).insert(captor.capture());
        verify(postCommentRepository, never()).selectById(any());
    }
}
