package com.chad.meaninglog.service.community;

import com.chad.meaninglog.dto.community.FeedItemResponse;
import com.chad.meaninglog.dto.community.PublicLogDetailResponse;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.MeaningLogRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import com.chad.meaninglog.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityFeedService {

    public enum FeedType { HOT, LATEST, FOLLOWING }

    private final PublicLogRepository publicLogRepository;
    private final MeaningLogRepository meaningLogRepository;
    private final UserAccountRepository userAccountRepository;
    private final CommunityRedisService redis;

    public List<FeedItemResponse> loadFeed(UserAccount viewer, FeedType type, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        List<Long> publicLogIds = switch (type) {
            case HOT -> redis.topHot(offset, size);
            case LATEST -> latestIds(offset, size);
            case FOLLOWING -> redis.followingFeed(viewer.getId(), offset, size);
        };
        if (publicLogIds.isEmpty()) {
            return List.of();
        }
        return assembleFeed(publicLogIds, viewer);
    }

    private List<Long> latestIds(int offset, int size) {
        return publicLogRepository.findLatestVisible(offset, size).stream()
                .map(PublicLog::getId)
                .toList();
    }

    public List<FeedItemResponse> loadUserPosts(UserAccount viewer, Long userId, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        List<PublicLog> posts = publicLogRepository.findByUserId(userId, offset, size);
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(PublicLog::getId).toList();
        return assembleFeed(ids, viewer);
    }

    public PublicLogDetailResponse loadDetail(UserAccount viewer, Long publicLogId) {
        PublicLog publicLog = publicLogRepository.findVisibleById(publicLogId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在或已下架"));
        MeaningLog log = meaningLogRepository.selectById(publicLog.getLogId());
        UserAccount author = userAccountRepository.selectById(publicLog.getUserId());

        boolean freshUv = redis.addUv(publicLogId, viewer.getId());
        if (freshUv) {
            redis.incrView(publicLogId);
        }

        long likes = redis.getCount(CommunityRedisKeys.countLike(publicLogId));
        long comments = redis.getCount(CommunityRedisKeys.countComment(publicLogId));
        long views = redis.getCount(CommunityRedisKeys.countView(publicLogId));
        boolean liked = redis.hasLiked(publicLogId, viewer.getId());
        boolean following = !viewer.getId().equals(publicLog.getUserId())
                && redis.isFollowing(viewer.getId(), publicLog.getUserId());
        return PublicLogDetailResponse.from(publicLog, log, author, likes, comments, views, liked, following);
    }

    private List<FeedItemResponse> assembleFeed(Collection<Long> publicLogIds, UserAccount viewer) {
        List<PublicLog> publicLogs = publicLogRepository.findByIds(publicLogIds);
        Map<Long, PublicLog> publicLogMap = orderedMap(publicLogIds, publicLogs, PublicLog::getId);

        Set<Long> logIds = publicLogs.stream().map(PublicLog::getLogId).collect(Collectors.toSet());
        Map<Long, MeaningLog> logMap = logIds.isEmpty()
                ? Collections.emptyMap()
                : meaningLogRepository.selectBatchIds(logIds).stream()
                .collect(Collectors.toMap(MeaningLog::getId, l -> l));

        Set<Long> authorIds = publicLogs.stream().map(PublicLog::getUserId).collect(Collectors.toSet());
        Map<Long, UserAccount> authorMap = authorIds.isEmpty()
                ? Collections.emptyMap()
                : userAccountRepository.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, u -> u));

        List<PublicLog> visible = publicLogIds.stream()
                .map(publicLogMap::get)
                .filter(p -> p != null && PublicLog.Status.VISIBLE.name().equals(p.getStatus()))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }

        // 批量把每篇帖子的 like/comment/view 计数 + viewer 点赞状态 + viewer 关注作者状态
        // 3 次网络往返完成 (MGET / GETBIT pipeline / SMISMEMBER), 而不是每帖 5 次
        List<Long> visibleIds = visible.stream().map(PublicLog::getId).toList();
        Set<Long> visibleAuthorIds = visible.stream()
                .map(PublicLog::getUserId)
                .collect(Collectors.toSet());
        Map<Long, CommunityRedisService.CommunityCounts> countsMap = redis.batchGetCounts(visibleIds);
        Set<Long> likedIds = viewer == null
                ? Collections.emptySet()
                : redis.batchHasLiked(visibleIds, viewer.getId());
        Set<Long> followedAuthorIds = viewer == null
                ? Collections.emptySet()
                : redis.batchIsFollowing(viewer.getId(), visibleAuthorIds);

        return visible.stream()
                .map(p -> {
                    MeaningLog log = logMap.get(p.getLogId());
                    UserAccount author = authorMap.get(p.getUserId());
                    CommunityRedisService.CommunityCounts c = countsMap.getOrDefault(
                            p.getId(), new CommunityRedisService.CommunityCounts(0L, 0L, 0L));
                    boolean liked = likedIds.contains(p.getId());
                    boolean following = viewer != null
                            && !viewer.getId().equals(p.getUserId())
                            && followedAuthorIds.contains(p.getUserId());
                    return FeedItemResponse.from(p, log, author,
                            c.like(), c.comment(), c.view(), liked, following);
                })
                .toList();
    }

    private static <T> Map<Long, T> orderedMap(Collection<Long> ids, List<T> items, java.util.function.Function<T, Long> keyFn) {
        Map<Long, T> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(keyFn.apply(item), item);
        }
        return map;
    }
}
