package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.community.CommentRequest;
import com.chad.meaninglog.dto.community.CommentResponse;
import com.chad.meaninglog.dto.community.FeedItemResponse;
import com.chad.meaninglog.dto.community.PublicLogDetailResponse;
import com.chad.meaninglog.dto.community.PublishResponse;
import com.chad.meaninglog.dto.community.UserProfileResponse;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.community.CommunityCommentService;
import com.chad.meaninglog.service.community.CommunityFeedService;
import com.chad.meaninglog.service.community.CommunityFollowService;
import com.chad.meaninglog.service.community.CommunityLikeService;
import com.chad.meaninglog.service.community.CommunityPublishService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
@Validated
public class CommunityController {

    private final CommunityPublishService publishService;
    private final CommunityFeedService feedService;
    private final CommunityLikeService likeService;
    private final CommunityCommentService commentService;
    private final CommunityFollowService followService;

    @PostMapping("/publish/{logId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PublishResponse publish(@AuthenticationPrincipal UserAccount user,
                                    @PathVariable Long logId) {
        return PublishResponse.from(publishService.publish(user, logId));
    }

    @DeleteMapping("/publish/{logId}")
    public void unpublish(@AuthenticationPrincipal UserAccount user,
                          @PathVariable Long logId) {
        publishService.unpublish(user, logId);
    }

    @GetMapping("/feed")
    public List<FeedItemResponse> feed(@AuthenticationPrincipal UserAccount user,
                                       @RequestParam(defaultValue = "hot") String type,
                                       @RequestParam(defaultValue = "1") @Min(1) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        CommunityFeedService.FeedType feedType = parseFeedType(type);
        return feedService.loadFeed(user, feedType, page, size);
    }

    @GetMapping("/posts/{publicLogId}")
    public PublicLogDetailResponse detail(@AuthenticationPrincipal UserAccount user,
                                          @PathVariable Long publicLogId) {
        return feedService.loadDetail(user, publicLogId);
    }

    @PostMapping("/like/{publicLogId}")
    public Map<String, Object> like(@AuthenticationPrincipal UserAccount user,
                                    @PathVariable Long publicLogId) {
        CommunityLikeService.LikeResult result = likeService.like(user, publicLogId);
        return Map.of("liked", result.liked(), "likeCount", result.likeCount());
    }

    @DeleteMapping("/like/{publicLogId}")
    public Map<String, Object> unlike(@AuthenticationPrincipal UserAccount user,
                                      @PathVariable Long publicLogId) {
        CommunityLikeService.LikeResult result = likeService.unlike(user, publicLogId);
        return Map.of("liked", result.liked(), "likeCount", result.likeCount());
    }

    @PostMapping("/comments/{publicLogId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@AuthenticationPrincipal UserAccount user,
                                       @PathVariable Long publicLogId,
                                       @Valid @RequestBody CommentRequest request) {
        return commentService.create(user, publicLogId, request);
    }

    @GetMapping("/comments/{publicLogId}")
    public List<CommentResponse> listComments(@PathVariable Long publicLogId,
                                              @RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return commentService.list(publicLogId, page, size);
    }

    @PostMapping("/follow/{userId}")
    public Map<String, Object> follow(@AuthenticationPrincipal UserAccount user,
                                       @PathVariable Long userId) {
        boolean changed = followService.follow(user, userId);
        return Map.of("following", true, "changed", changed);
    }

    @DeleteMapping("/follow/{userId}")
    public Map<String, Object> unfollow(@AuthenticationPrincipal UserAccount user,
                                         @PathVariable Long userId) {
        boolean changed = followService.unfollow(user, userId);
        return Map.of("following", false, "changed", changed);
    }

    @GetMapping("/users/{userId}")
    public UserProfileResponse userProfile(@AuthenticationPrincipal UserAccount viewer,
                                            @PathVariable Long userId) {
        return followService.profile(viewer, userId);
    }

    @GetMapping("/users/{userId}/posts")
    public List<FeedItemResponse> userPosts(@AuthenticationPrincipal UserAccount viewer,
                                            @PathVariable Long userId,
                                            @RequestParam(defaultValue = "1") @Min(1) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return feedService.loadUserPosts(viewer, userId, page, size);
    }

    private CommunityFeedService.FeedType parseFeedType(String type) {
        return switch (type == null ? "" : type.toLowerCase()) {
            case "latest" -> CommunityFeedService.FeedType.LATEST;
            case "following" -> CommunityFeedService.FeedType.FOLLOWING;
            default -> CommunityFeedService.FeedType.HOT;
        };
    }
}
