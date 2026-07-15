package com.chad.meaninglog.service.community;

import com.chad.meaninglog.dto.community.FeedItemResponse;
import com.chad.meaninglog.dto.community.PublicLogDetailResponse;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import com.chad.meaninglog.repository.UserFollowRepository;
import com.chad.meaninglog.time.BusinessTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityFeedService {

    public enum FeedType { HOT, LATEST, FOLLOWING }

    private final PublicLogRepository publicLogRepository;
    private final MeaningLogRepository meaningLogRepository;
    private final UserAccountRepository userAccountRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserFollowRepository userFollowRepository;
    private final CommunityRedisService redis;
    private final CommunityRedisBatchService redisBatch;
    private final Clock businessClock;

    public List<FeedItemResponse> loadFeed(UserAccount viewer, FeedType type, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        if (type == FeedType.FOLLOWING) {
            List<PublicLog> posts = publicLogRepository.findFollowingVisible(
                    viewer.getId(), offset, size);
            rebuildFollowingFeed(viewer.getId(), posts);
            return assembleFeed(posts.stream().map(PublicLog::getId).toList(), viewer);
        }
        List<Long> publicLogIds = loadCachedIds(viewer, type, offset, size);
        List<FeedItemResponse> response = assembleFeed(publicLogIds, viewer);
        if (!response.isEmpty() || type == FeedType.LATEST) {
            return response;
        }
        List<PublicLog> fallback = publicLogRepository.findLatestVisible(offset, size);
        return assembleFeed(fallback.stream().map(PublicLog::getId).toList(), viewer);
    }

    private List<Long> loadCachedIds(UserAccount viewer, FeedType type, int offset, int size) {
        if (type == FeedType.LATEST) {
            return publicLogRepository.findLatestVisible(offset, size).stream()
                    .map(PublicLog::getId)
                    .toList();
        }
        try {
            return redis.topHot(offset, size);
        } catch (DataAccessException exception) {
            log.warn("社区 Feed Redis 不可用, 降级到 MySQL: type={}, userId={}",
                    type, viewer == null ? null : viewer.getId());
            return List.of();
        }
    }

    public List<FeedItemResponse> loadUserPosts(UserAccount viewer, Long userId, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        List<PublicLog> posts = publicLogRepository.findByUserId(userId, offset, size);
        return assembleFeed(posts.stream().map(PublicLog::getId).toList(), viewer);
    }

    public PublicLogDetailResponse loadDetail(UserAccount viewer, Long publicLogId) {
        PublicLog publicLog = publicLogRepository.findVisibleById(publicLogId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "帖子不存在或已下架"));
        MeaningLog log = meaningLogRepository.selectById(publicLog.getLogId());
        UserAccount author = userAccountRepository.selectById(publicLog.getUserId());
        if (log == null || author == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在或已下架");
        }

        long views = recordView(publicLog, viewer);
        boolean liked = postLikeRepository
                .findByPublicLogIdAndUserId(publicLogId, viewer.getId()).isPresent();
        boolean following = !viewer.getId().equals(publicLog.getUserId())
                && userFollowRepository.findByFollowerAndFollowee(
                viewer.getId(), publicLog.getUserId()).isPresent();
        return PublicLogDetailResponse.from(publicLog, log, author,
                publicLog.getLikeCount(), publicLog.getCommentCount(), views, liked, following);
    }

    private long recordView(PublicLog publicLog, UserAccount viewer) {
        try {
            LocalDate businessDate = LocalDate.now(businessClock);
            return redis.recordView(publicLog.getId(), viewer.getId(),
                    businessDate, publicLog.getViewCount());
        } catch (DataAccessException exception) {
            log.warn("记录社区浏览量时 Redis 不可用, 返回 MySQL 已持久化计数: publicLogId={}, userId={}",
                    publicLog.getId(), viewer.getId());
            return publicLog.getViewCount();
        }
    }

    private List<FeedItemResponse> assembleFeed(Collection<Long> publicLogIds, UserAccount viewer) {
        if (publicLogIds == null || publicLogIds.isEmpty()) {
            return List.of();
        }
        List<PublicLog> publicLogs = publicLogRepository.findByIds(publicLogIds);
        Map<Long, PublicLog> publicLogMap = orderedMap(publicLogs, PublicLog::getId);

        Set<Long> logIds = publicLogs.stream().map(PublicLog::getLogId).collect(Collectors.toSet());
        Map<Long, MeaningLog> logMap = logIds.isEmpty() ? Collections.emptyMap()
                : meaningLogRepository.selectByIds(logIds).stream()
                .collect(Collectors.toMap(MeaningLog::getId, value -> value));
        Set<Long> authorIds = publicLogs.stream().map(PublicLog::getUserId).collect(Collectors.toSet());
        Map<Long, UserAccount> authorMap = authorIds.isEmpty() ? Collections.emptyMap()
                : userAccountRepository.selectByIds(authorIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, value -> value));

        List<PublicLog> visible = publicLogIds.stream()
                .map(publicLogMap::get)
                .filter(post -> post != null
                        && PublicLog.Status.VISIBLE.name().equals(post.getStatus())
                        && logMap.containsKey(post.getLogId())
                        && authorMap.containsKey(post.getUserId()))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }

        List<Long> visibleIds = visible.stream().map(PublicLog::getId).toList();
        Set<Long> likedIds = viewer == null ? Collections.emptySet()
                : Set.copyOf(postLikeRepository.findLikedPublicLogIds(viewer.getId(), visibleIds));
        Set<Long> visibleAuthorIds = visible.stream().map(PublicLog::getUserId).collect(Collectors.toSet());
        Set<Long> followedAuthorIds = viewer == null ? Collections.emptySet()
                : Set.copyOf(userFollowRepository.findFolloweeIds(viewer.getId(), visibleAuthorIds));

        return visible.stream().map(post -> FeedItemResponse.from(
                        post, logMap.get(post.getLogId()), authorMap.get(post.getUserId()),
                        post.getLikeCount(), post.getCommentCount(), post.getViewCount(),
                        likedIds.contains(post.getId()),
                        viewer != null && !viewer.getId().equals(post.getUserId())
                                && followedAuthorIds.contains(post.getUserId())))
                .toList();
    }

    private void rebuildFollowingFeed(Long userId, List<PublicLog> posts) {
        if (posts.isEmpty()) {
            return;
        }
        try {
            redisBatch.rebuildUserFeed(userId, posts.stream()
                    .map(post -> new CommunityRedisBatchService.FeedEntry(
                            post.getId(), post.getPublishedAt()
                            .atZone(BusinessTime.ZONE_ID).toEpochSecond()))
                    .toList());
        } catch (DataAccessException exception) {
            log.warn("重建 following Feed 缓存失败, userId={}", userId);
        }
    }

    private static <T> Map<Long, T> orderedMap(
            List<T> items, java.util.function.Function<T, Long> keyFunction) {
        Map<Long, T> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(keyFunction.apply(item), item);
        }
        return map;
    }
}
