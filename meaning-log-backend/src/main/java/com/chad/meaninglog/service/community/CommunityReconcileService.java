package com.chad.meaninglog.service.community;

import com.chad.meaninglog.dto.community.CommunityCountRow;
import com.chad.meaninglog.entity.PostLike;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.repository.CommunityCounterBatchRepository;
import com.chad.meaninglog.repository.PostCommentRepository;
import com.chad.meaninglog.repository.PostLikeRepository;
import com.chad.meaninglog.repository.PublicLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityReconcileService {

    private static final int MAX_CHECK = 200;
    private static final int DB_BATCH_SIZE = 50;
    private static final long RECENT_DAYS = 30L;

    private final PublicLogRepository publicLogRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final CommunityCounterBatchRepository counterBatchRepository;
    private final CommunityRedisBatchService redisBatch;
    private final HotScoreCalculator hotScoreCalculator;
    private final Clock businessClock;

    public ReconcileResult reconcileRecent() {
        List<PublicLog> candidates = publicLogRepository.findRecentVisible(
                LocalDateTime.now(businessClock).minusDays(RECENT_DAYS), MAX_CHECK);
        int corrected = 0;
        int rebuilt = 0;
        for (int start = 0; start < candidates.size(); start += DB_BATCH_SIZE) {
            List<PublicLog> batch = candidates.subList(start,
                    Math.min(start + DB_BATCH_SIZE, candidates.size()));
            BatchResult result = reconcileBatch(batch);
            corrected += result.corrected();
            rebuilt += result.rebuilt();
        }
        return new ReconcileResult(candidates.size(), corrected, rebuilt);
    }

    private BatchResult reconcileBatch(List<PublicLog> batch) {
        List<Long> ids = batch.stream().map(PublicLog::getId).toList();
        List<PostLike> likes = postLikeRepository.findByPublicLogIds(ids);
        Map<Long, List<Long>> likedUsers = likes.stream().collect(Collectors.groupingBy(
                PostLike::getPublicLogId,
                Collectors.mapping(PostLike::getUserId, Collectors.toList())));
        Map<Long, Long> likeCounts = likedUsers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size()));
        Map<Long, Long> commentCounts = countMap(postCommentRepository.countByPublicLogIds(ids));

        List<CommunityCounterBatchRepository.CountSnapshot> corrections = new ArrayList<>();
        for (PublicLog post : batch) {
            long likesCount = likeCounts.getOrDefault(post.getId(), 0L);
            long commentsCount = commentCounts.getOrDefault(post.getId(), 0L);
            if (likesCount != post.getLikeCount() || commentsCount != post.getCommentCount()) {
                corrections.add(new CommunityCounterBatchRepository.CountSnapshot(
                        post.getId(), likesCount, commentsCount, post.getCacheVersion()));
            }
        }
        int corrected = counterBatchRepository.updateCountsIfVersion(corrections);

        Map<Long, PublicLog> current = publicLogRepository.selectByIds(ids).stream()
                .collect(Collectors.toMap(PublicLog::getId, value -> value));
        int rebuilt = 0;
        for (PublicLog snapshot : batch) {
            long likesCount = likeCounts.getOrDefault(snapshot.getId(), 0L);
            long commentsCount = commentCounts.getOrDefault(snapshot.getId(), 0L);
            boolean correctionExpected = likesCount != snapshot.getLikeCount()
                    || commentsCount != snapshot.getCommentCount();
            long expectedVersion = snapshot.getCacheVersion() + (correctionExpected ? 1L : 0L);
            PublicLog latest = current.get(snapshot.getId());
            if (latest == null || latest.getCacheVersion() != expectedVersion
                    || latest.getLikeCount() != likesCount
                    || latest.getCommentCount() != commentsCount) {
                continue;
            }
            double score = hotScoreCalculator.score(likesCount, commentsCount,
                    latest.getViewCount(), latest.getPublishedAt());
            if (redisBatch.replaceFullPostState(latest.getId(), latest.getCacheVersion(),
                    likesCount, commentsCount, latest.getViewCount(), score,
                    likedUsers.getOrDefault(latest.getId(), Collections.emptyList()))) {
                rebuilt++;
            }
        }
        return new BatchResult(corrected, rebuilt);
    }

    private Map<Long, Long> countMap(Collection<CommunityCountRow> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (CommunityCountRow row : rows) {
            result.put(row.getPublicLogId(), row.getTotal());
        }
        return result;
    }

    private record BatchResult(int corrected, int rebuilt) {
    }

    public record ReconcileResult(int checked, int corrected, int rebuilt) {
    }
}
