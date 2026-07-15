package com.chad.meaninglog.service.community;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 社区 Redis 原子操作。MySQL 是事实来源，本类只维护可重建的派生状态。
 */
@Service
@RequiredArgsConstructor
public class CommunityRedisService {

    static final long FEED_KEEP_SIZE = 500L;
    static final Duration FEED_TTL = Duration.ofDays(30);
    private static final long HOT_KEEP_SIZE = 1000L;
    private static final Duration PV_HLL_TTL = Duration.ofDays(90);

    private static final DefaultRedisScript<Long> POST_STATE_SCRIPT = new DefaultRedisScript<>(
            "local currentVersion = tonumber(redis.call('get', KEYS[1]) or '-1')\n"
                    + "local incomingVersion = tonumber(ARGV[1])\n"
                    + "if incomingVersion < currentVersion then return 0 end\n"
                    + "redis.call('set', KEYS[2], ARGV[3])\n"
                    + "redis.call('set', KEYS[3], ARGV[4])\n"
                    + "local currentViews = tonumber(redis.call('get', KEYS[4]) or '0')\n"
                    + "local dbViews = tonumber(ARGV[5])\n"
                    + "if currentViews < dbViews then currentViews = dbViews end\n"
                    + "redis.call('set', KEYS[4], tostring(currentViews))\n"
                    + "if tonumber(ARGV[7]) >= 0 then\n"
                    + "  redis.call('setbit', KEYS[5], tonumber(ARGV[7]), tonumber(ARGV[8]))\n"
                    + "end\n"
                    + "redis.call('zadd', KEYS[6], tonumber(ARGV[6]), ARGV[2])\n"
                    + "redis.call('set', KEYS[1], ARGV[1])\n"
                    + "return 1",
            Long.class
    );

    private static final DefaultRedisScript<Long> FOLLOW_STATE_SCRIPT = new DefaultRedisScript<>(
            "local currentVersion = tonumber(redis.call('get', KEYS[3]) or '-1')\n"
                    + "local incomingVersion = tonumber(ARGV[1])\n"
                    + "if incomingVersion < currentVersion then return 0 end\n"
                    + "if ARGV[4] == '1' then\n"
                    + "  redis.call('sadd', KEYS[1], ARGV[2])\n"
                    + "  redis.call('sadd', KEYS[2], ARGV[3])\n"
                    + "else\n"
                    + "  redis.call('srem', KEYS[1], ARGV[2])\n"
                    + "  redis.call('srem', KEYS[2], ARGV[3])\n"
                    + "end\n"
                    + "redis.call('set', KEYS[3], ARGV[1])\n"
                    + "return 1",
            Long.class
    );

    private static final DefaultRedisScript<Long> VIEW_SCRIPT = new DefaultRedisScript<>(
            "local added = redis.call('pfadd', KEYS[1], ARGV[1])\n"
                    + "redis.call('expire', KEYS[1], tonumber(ARGV[2]))\n"
                    + "local current = tonumber(redis.call('get', KEYS[2]) or '0')\n"
                    + "local persisted = tonumber(ARGV[3])\n"
                    + "if current < persisted then\n"
                    + "  current = persisted\n"
                    + "  redis.call('set', KEYS[2], tostring(current))\n"
                    + "end\n"
                    + "if added == 1 then\n"
                    + "  current = redis.call('incr', KEYS[2])\n"
                    + "  redis.call('sadd', KEYS[3], ARGV[4])\n"
                    + "end\n"
                    + "return current",
            Long.class
    );

    private static final DefaultRedisScript<List> CLAIM_DIRTY_SCRIPT = new DefaultRedisScript<>(
            "local expired = redis.call('zrangebyscore', KEYS[2], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])\n"
                    + "for _, id in ipairs(expired) do redis.call('zrem', KEYS[2], id) redis.call('sadd', KEYS[1], id) end\n"
                    + "local claimed = {}\n"
                    + "local scanned = 0\n"
                    + "local max = tonumber(ARGV[2])\n"
                    + "while #claimed < max and scanned < max * 4 do\n"
                    + "  local id = redis.call('spop', KEYS[1])\n"
                    + "  if not id then break end\n"
                    + "  if redis.call('zscore', KEYS[2], id) then redis.call('sadd', KEYS[1], id)\n"
                    + "  else redis.call('zadd', KEYS[2], ARGV[3], id) table.insert(claimed, id) end\n"
                    + "  scanned = scanned + 1\n"
                    + "end\n"
                    + "return claimed",
            List.class
    );

    private static final DefaultRedisScript<Long> RETRY_DIRTY_SCRIPT = new DefaultRedisScript<>(
            "for i = 1, #ARGV do redis.call('zrem', KEYS[2], ARGV[i]) redis.call('sadd', KEYS[1], ARGV[i]) end\n"
                    + "return #ARGV",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public boolean hasLiked(Long publicLogId, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .getBit(CommunityRedisKeys.likeBitmap(publicLogId), userId));
    }

    public long getCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    public boolean replacePostState(Long publicLogId,
                                    long cacheVersion,
                                    long likes,
                                    long comments,
                                    long views,
                                    double hotScore,
                                    Long userId,
                                    boolean liked) {
        Long applied = redisTemplate.execute(POST_STATE_SCRIPT, List.of(
                        CommunityRedisKeys.postVersion(publicLogId),
                        CommunityRedisKeys.countLike(publicLogId),
                        CommunityRedisKeys.countComment(publicLogId),
                        CommunityRedisKeys.countView(publicLogId),
                        CommunityRedisKeys.likeBitmap(publicLogId),
                        CommunityRedisKeys.HOT_GLOBAL),
                String.valueOf(cacheVersion), String.valueOf(publicLogId),
                String.valueOf(likes), String.valueOf(comments), String.valueOf(views),
                String.valueOf(hotScore), String.valueOf(userId == null ? -1L : userId),
                liked ? "1" : "0");
        return Long.valueOf(1L).equals(applied);
    }

    public void clearPostState(Long publicLogId) {
        redisTemplate.delete(List.of(
                CommunityRedisKeys.postVersion(publicLogId),
                CommunityRedisKeys.countLike(publicLogId),
                CommunityRedisKeys.countComment(publicLogId),
                CommunityRedisKeys.countView(publicLogId),
                CommunityRedisKeys.likeBitmap(publicLogId)
        ));
        redisTemplate.opsForZSet().remove(CommunityRedisKeys.HOT_GLOBAL, String.valueOf(publicLogId));
        redisTemplate.opsForSet().remove(CommunityRedisKeys.DIRTY_COUNTERS, String.valueOf(publicLogId));
        redisTemplate.opsForZSet().remove(
                CommunityRedisKeys.DIRTY_COUNTERS_PROCESSING, String.valueOf(publicLogId));
    }

    public boolean replaceFollowState(long repairVersion,
                                      Long followerId,
                                      Long followeeId,
                                      boolean following) {
        Long applied = redisTemplate.execute(FOLLOW_STATE_SCRIPT, List.of(
                        CommunityRedisKeys.following(followerId),
                        CommunityRedisKeys.follower(followeeId),
                        CommunityRedisKeys.followVersion(followerId, followeeId)),
                String.valueOf(repairVersion), String.valueOf(followeeId),
                String.valueOf(followerId), following ? "1" : "0");
        return Long.valueOf(1L).equals(applied);
    }

    public long recordView(Long publicLogId,
                           Long userId,
                           LocalDate businessDate,
                           long persistedViews) {
        Long value = redisTemplate.execute(VIEW_SCRIPT, List.of(
                        CommunityRedisKeys.pvHll(publicLogId, businessDate),
                        CommunityRedisKeys.countView(publicLogId),
                        CommunityRedisKeys.DIRTY_COUNTERS),
                String.valueOf(userId), String.valueOf(PV_HLL_TTL.getSeconds()),
                String.valueOf(persistedViews), String.valueOf(publicLogId));
        return value == null ? persistedViews : value;
    }

    @SuppressWarnings("unchecked")
    public List<String> claimDirty(int max, long nowMillis, long leaseUntilMillis) {
        List<String> ids = redisTemplate.execute(CLAIM_DIRTY_SCRIPT,
                List.of(CommunityRedisKeys.DIRTY_COUNTERS,
                        CommunityRedisKeys.DIRTY_COUNTERS_PROCESSING),
                String.valueOf(nowMillis), String.valueOf(max), String.valueOf(leaseUntilMillis));
        return ids == null ? Collections.emptyList() : ids;
    }

    public void ackDirty(Collection<Long> publicLogIds) {
        if (publicLogIds == null || publicLogIds.isEmpty()) {
            return;
        }
        redisTemplate.opsForZSet().remove(CommunityRedisKeys.DIRTY_COUNTERS_PROCESSING,
                publicLogIds.stream().map(String::valueOf).toArray());
    }

    public void retryDirty(Collection<Long> publicLogIds) {
        if (publicLogIds == null || publicLogIds.isEmpty()) {
            return;
        }
        String[] ids = publicLogIds.stream().map(String::valueOf).toArray(String[]::new);
        redisTemplate.execute(RETRY_DIRTY_SCRIPT,
                List.of(CommunityRedisKeys.DIRTY_COUNTERS,
                        CommunityRedisKeys.DIRTY_COUNTERS_PROCESSING),
                (Object[]) ids);
    }

    public void discardDirty(String rawId) {
        redisTemplate.opsForZSet().remove(CommunityRedisKeys.DIRTY_COUNTERS_PROCESSING, rawId);
    }

    public List<Long> topHot(int offset, int size) {
        return toLongList(redisTemplate.opsForZSet().reverseRange(
                CommunityRedisKeys.HOT_GLOBAL, offset, (long) offset + size - 1));
    }

    public void trimHot() {
        Long total = redisTemplate.opsForZSet().zCard(CommunityRedisKeys.HOT_GLOBAL);
        if (total != null && total > HOT_KEEP_SIZE) {
            redisTemplate.opsForZSet().removeRange(
                    CommunityRedisKeys.HOT_GLOBAL, 0, total - HOT_KEEP_SIZE - 1);
        }
    }

    public void pushToFollowerFeeds(Collection<Long> followerIds,
                                    Long publicLogId,
                                    long timestampSeconds) {
        if (followerIds == null || followerIds.isEmpty()) {
            return;
        }
        String member = String.valueOf(publicLogId);
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConnection = (StringRedisConnection) connection;
            for (Long followerId : followerIds) {
                String feedKey = CommunityRedisKeys.feedUser(followerId);
                stringConnection.zAdd(feedKey, timestampSeconds, member);
                stringConnection.zRemRange(feedKey, 0, -FEED_KEEP_SIZE - 1);
                stringConnection.expire(feedKey, FEED_TTL.getSeconds());
            }
            return null;
        });
    }

    private static List<Long> toLongList(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>(raw.size());
        for (String value : raw) {
            result.add(Long.parseLong(value));
        }
        return result;
    }
}
