package com.chad.meaninglog.service.community;

import com.chad.meaninglog.dto.community.CommentRequest;
import com.chad.meaninglog.dto.community.CommentResponse;
import com.chad.meaninglog.entity.PostComment;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.PostCommentRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityCommentService {

    private static final int MAX_COMMENT_LENGTH = 500;

    private final PublicLogRepository publicLogRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserAccountRepository userAccountRepository;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final CommunityRedisService redis;
    private final HotScoreCalculator hotScoreCalculator;
    private final NotificationService notificationService;

    @Transactional
    public CommentResponse create(UserAccount user, Long publicLogId, CommentRequest request) {
        PublicLog publicLog = publicLogRepository.findVisibleById(publicLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在"));

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论内容不能为空");
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论最长 " + MAX_COMMENT_LENGTH + " 字");
        }
        String hit = sensitiveWordFilter.firstHit(content);
        if (hit != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论包含敏感词, 请修改后再试");
        }

        Long parentId = request.getParentId();
        if (parentId != null) {
            PostComment parent = postCommentRepository.selectById(parentId);
            if (parent == null || !publicLogId.equals(parent.getPublicLogId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父评论不存在或不属于当前帖子");
            }
        }

        PostComment comment = new PostComment();
        comment.setPublicLogId(publicLogId);
        comment.setUserId(user.getId());
        comment.setParentId(parentId);
        comment.setContent(content);
        postCommentRepository.insert(comment);

        long comments = redis.incrComment(publicLogId);
        long likes = redis.getCount(CommunityRedisKeys.countLike(publicLogId));
        long views = redis.getCount(CommunityRedisKeys.countView(publicLogId));
        redis.updateHotScore(publicLog.getId(),
                hotScoreCalculator.score(likes, comments, views, publicLog.getPublishedAt()));

        notificationService.notifyComment(user, publicLog.getUserId(), publicLogId, comment.getId(), content);
        return CommentResponse.from(comment, user);
    }

    public List<CommentResponse> list(Long publicLogId, int page, int size) {
        publicLogRepository.findVisibleById(publicLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在"));
        int offset = Math.max(0, page - 1) * size;
        List<PostComment> comments = postCommentRepository.findByPublicLogId(publicLogId, offset, size);
        if (comments.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = comments.stream().map(PostComment::getUserId).collect(Collectors.toSet());
        Map<Long, UserAccount> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userAccountRepository.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, u -> u));
        return comments.stream()
                .map(c -> CommentResponse.from(c, userMap.get(c.getUserId())))
                .toList();
    }
}
