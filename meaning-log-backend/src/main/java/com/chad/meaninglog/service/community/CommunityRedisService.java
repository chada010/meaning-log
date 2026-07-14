package com.chad.meaninglog.service.community;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 社区模块 Redis 操作封装。所有 key 通过 {@link CommunityRedisKeys} 生成。
 */
@Service
@RequiredArgsConstructor
public class CommunityRedisService {

    private static final long FEED_KEEP_SIZE = 500;
    private static final long HOT_KEEP_SIZE = 1000;
    private static final Duration FEED_TTL = Duration.ofDays(30);
    private static final Duration PV_HLL_TTL = Duration.ofDays(90);

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then\n"
                    + "  return redis.call('del', KEYS[1])\n"
                    + "else\n"
                    + "  return 0\n"
                    + "end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    // -------- 点赞 Bitmap --------

    public boolean hasLiked(Long publicLogId, Long userId) {
        Boolean bit = redisTemplate.opsForValue().getBit(CommunityRedisKeys.likeBitmap(publicLogId), userId);
        return Boolean.TRUE.equals(bit);
    }

    public boolean markLiked(Long publicLogId, Long userId) {
        Boolean previous = redisTemplate.opsForValue()
                .setBit(CommunityRedisKeys.likeBitmap(publicLogId), userId, true);
        return !Boolean.TRUE.equals(previous);
    }

    public boolean markUnliked(Long publicLogId, Long userId) {
        Boolean previous = redisTemplate.opsForValue()
                .setBit(CommunityRedisKeys.likeBitmap(publicLogId), userId, false);
        return Boolean.TRUE.equals(previous);
    }

    public long bitmapLikeCount(Long publicLogId) {
        Long count = redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Long>) connection ->
                connection.stringCommands().bitCount(CommunityRedisKeys.likeBitmap(publicLogId).getBytes()));
        return count == null ? 0L : count;
    }

    // -------- 计数器 --------

    public long incrLike(Long publicLogId) {
        Long v = redisTemplate.opsForValue().increment(CommunityRedisKeys.countLike(publicLogId));
        markDirty(publicLogId);
        return v == null ? 0L : v;
    }

    public long decrLike(Long publicLogId) {
        Long v = redisTemplate.opsForValue().decrement(CommunityRedisKeys.countLike(publicLogId));
        markDirty(publicLogId);
        return v == null ? 0L : v;
    }

    public long incrComment(Long publicLogId) {
        Long v = redisTemplate.opsForValue().increment(CommunityRedisKeys.countComment(publicLogId));
        markDirty(publicLogId);
        return v == null ? 0L : v;
    }

    public long incrView(Long publicLogId) {
        Long v = redisTemplate.opsForValue().increment(CommunityRedisKeys.countView(publicLogId));
        markDirty(publicLogId);
        return v == null ? 0L : v;
    }

    public long getCount(String key) {
        String v = redisTemplate.opsForValue().get(key);
        return v == null ? 0L : Long.parseLong(v);
    }

    public void seedCounts(Long publicLogId, long likes, long comments, long views) {
        StringRedisTemplate t = redisTemplate;
        t.opsForValue().setIfAbsent(CommunityRedisKeys.countLike(publicLogId), String.valueOf(likes));
        t.opsForValue().setIfAbsent(CommunityRedisKeys.countComment(publicLogId), String.valueOf(comments));
        t.opsForValue().setIfAbsent(CommunityRedisKeys.countView(publicLogId), String.valueOf(views));
    }

    public void clearCounters(Long publicLogId) {
        redisTemplate.delete(List.of(
                CommunityRedisKeys.countLike(publicLogId),
                CommunityRedisKeys.countComment(publicLogId),
                CommunityRedisKeys.countView(publicLogId),
                CommunityRedisKeys.likeBitmap(publicLogId)
        ));
    }

    private void markDirty(Long publicLogId) {
        redisTemplate.opsForSet().add(CommunityRedisKeys.DIRTY_COUNTERS, String.valueOf(publicLogId));
    }

    public Set<String> popDirty(int max) {
        List<String> ids = redisTemplate.opsForSet().pop(CommunityRedisKeys.DIRTY_COUNTERS, max);
        return ids == null || ids.isEmpty() ? Collections.emptySet() : new HashSet<>(ids);
    }

    // -------- 热榜 ZSet --------

    public void updateHotScore(Long publicLogId, double score) {
        redisTemplate.opsForZSet().add(CommunityRedisKeys.HOT_GLOBAL, String.valueOf(publicLogId), score);
    }

    public void removeFromHot(Long publicLogId) {
        redisTemplate.opsForZSet().remove(CommunityRedisKeys.HOT_GLOBAL, String.valueOf(publicLogId));
    }

    public List<Long> topHot(int offset, int size) {
        Set<String> raw = redisTemplate.opsForZSet()
                .reverseRange(CommunityRedisKeys.HOT_GLOBAL, offset, (long) offset + size - 1);
        return toLongList(raw);
    }

    public void trimHot() {
        Long total = redisTemplate.opsForZSet().zCard(CommunityRedisKeys.HOT_GLOBAL);
        if (total != null && total > HOT_KEEP_SIZE) {
            redisTemplate.opsForZSet()
                    .removeRange(CommunityRedisKeys.HOT_GLOBAL, 0, total - HOT_KEEP_SIZE - 1);
        }
    }

    // -------- Feed ZSet (推模式) --------

    public void pushToFollowerFeeds(Collection<Long> followerIds, Long publicLogId, long timestampSeconds) {
        if (followerIds == null || followerIds.isEmpty()) {
            return;
        }
        String member = String.valueOf(publicLogId);
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long followerId : followerIds) {
                String feedKey = CommunityRedisKeys.feedUser(followerId);
                src.zAdd(feedKey, timestampSeconds, member);
                src.zRemRange(feedKey, 0, -FEED_KEEP_SIZE - 1);
                src.expire(feedKey, FEED_TTL.getSeconds());
            }
            return null;
        });
    }

    public void removeFromAllFeeds(Long publicLogId) {
        // 撤回时无法枚举所有 feed，交由前端与 feed 过滤兜底（feed 展示时校验 PublicLog 存在性）
    }

    public List<Long> followingFeed(Long userId, int offset, int size) {
        Set<String> raw = redisTemplate.opsForZSet()
                .reverseRange(CommunityRedisKeys.feedUser(userId), offset, (long) offset + size - 1);
        return toLongList(raw);
    }

    // -------- 关注 Set --------

    public void addFollow(Long followerId, Long followeeId) {
        redisTemplate.opsForSet().add(CommunityRedisKeys.following(followerId), String.valueOf(followeeId));
        redisTemplate.opsForSet().add(CommunityRedisKeys.follower(followeeId), String.valueOf(followerId));
    }

    public void removeFollow(Long followerId, Long followeeId) {
        redisTemplate.opsForSet().remove(CommunityRedisKeys.following(followerId), String.valueOf(followeeId));
        redisTemplate.opsForSet().remove(CommunityRedisKeys.follower(followeeId), String.valueOf(followerId));
    }

    public boolean isFollowing(Long followerId, Long followeeId) {
        Boolean member = redisTemplate.opsForSet()
                .isMember(CommunityRedisKeys.following(followerId), String.valueOf(followeeId));
        return Boolean.TRUE.equals(member);
    }

    public Set<String> followerIds(Long userId) {
        Set<String> members = redisTemplate.opsForSet().members(CommunityRedisKeys.follower(userId));
        return members == null ? Collections.emptySet() : members;
    }

    // -------- HyperLogLog UV --------

    public boolean addUv(Long publicLogId, Long userId) {
        String key = CommunityRedisKeys.pvHll(publicLogId, LocalDate.now());
        Long added = redisTemplate.opsForHyperLogLog().add(key, String.valueOf(userId));
        redisTemplate.expire(key, PV_HLL_TTL);
        return added != null && added > 0;
    }

    public long uvCount(Long publicLogId, LocalDate date) {
        return redisTemplate.opsForHyperLogLog().size(CommunityRedisKeys.pvHll(publicLogId, date));
    }

    // -------- 分布式锁 (SET NX PX) --------

    public String acquireLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void releaseLock(String key, String token) {
        if (token == null) {
            return;
        }
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(key), token);
    }

    // -------- Pub/Sub 通知 --------

    public void publishNotification(Long receiverId, String payload) {
        redisTemplate.convertAndSend(CommunityRedisKeys.notifyChannel(receiverId), payload);
    }

    // -------- 批量读 (消除 Feed / HotScoreJob 里的 N+1 Redis RTT) --------

    public record CommunityCounts(long like, long comment, long view) {}

    /**
     * 一次 MGET 拉齐 N 篇帖子的 like/comment/view 计数, 把 3N 次 RTT 降为 1 次.
     */
    public Map<Long, CommunityCounts> batchGetCounts(Collection<Long> publicLogIds) {
        if (publicLogIds == null || publicLogIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ordered = new ArrayList<>(new HashSet<>(publicLogIds));
        List<String> keys = new ArrayList<>(ordered.size() * 3);
        for (Long id : ordered) {
            keys.add(CommunityRedisKeys.countLike(id));
            keys.add(CommunityRedisKeys.countComment(id));
            keys.add(CommunityRedisKeys.countView(id));
        }
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        Map<Long, CommunityCounts> map = new HashMap<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            long like = parseLong(values == null ? null : values.get(i * 3));
            long comment = parseLong(values == null ? null : values.get(i * 3 + 1));
            long view = parseLong(values == null ? null : values.get(i * 3 + 2));
            map.put(ordered.get(i), new CommunityCounts(like, comment, view));
        }
        return map;
    }

    /**
     * 用 pipeline 把 N 次 GETBIT 打包成 1 次 RTT, 返回 viewer 已点赞的帖子 id 集合.
     */
    public Set<Long> batchHasLiked(Collection<Long> publicLogIds, Long userId) {
        if (userId == null || publicLogIds == null || publicLogIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> ordered = new ArrayList<>(new HashSet<>(publicLogIds));
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> ops) {
                RedisOperations<String, String> str = (RedisOperations<String, String>) ops;
                for (Long id : ordered) {
                    str.opsForValue().getBit(CommunityRedisKeys.likeBitmap(id), userId);
                }
                return null;
            }
        });
        Set<Long> liked = new HashSet<>();
        for (int i = 0; i < ordered.size() && i < results.size(); i++) {
            if (Boolean.TRUE.equals(results.get(i))) {
                liked.add(ordered.get(i));
            }
        }
        return liked;
    }

    /**
     * 用 SMISMEMBER 一次 RTT 判断 viewer 关注了哪些 author, 返回已关注的 author id 集合.
     */
    public Set<Long> batchIsFollowing(Long viewerId, Collection<Long> authorIds) {
        if (viewerId == null || authorIds == null || authorIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> ordered = new ArrayList<>(new HashSet<>(authorIds));
        Object[] members = new Object[ordered.size()];
        for (int i = 0; i < ordered.size(); i++) {
            members[i] = String.valueOf(ordered.get(i));
        }
        Map<Object, Boolean> memberships = redisTemplate.opsForSet()
                .isMember(CommunityRedisKeys.following(viewerId), members);
        Set<Long> following = new HashSet<>();
        if (memberships == null) {
            return following;
        }
        for (Long id : ordered) {
            if (Boolean.TRUE.equals(memberships.get(String.valueOf(id)))) {
                following.add(id);
            }
        }
        return following;
    }

    /**
     * 用 pipeline 把 N 次 ZADD 打包成 1 次 RTT, HotScoreJob 重算 K 篇帖子时把 K 次往返降为 1 次.
     */
    public void batchUpdateHotScore(Map<Long, Double> scoreById) {
        if (scoreById == null || scoreById.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Map.Entry<Long, Double> entry : scoreById.entrySet()) {
                src.zAdd(CommunityRedisKeys.HOT_GLOBAL, entry.getValue(), String.valueOf(entry.getKey()));
            }
            return null;
        });
    }

    // -------- helpers --------

    private static long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static List<Long> toLongList(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>(raw.size());
        for (String s : raw) {
            result.add(Long.parseLong(s));
        }
        return result;
    }
}
