package com.chad.meaninglog.service.community;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityRedisBatchService {

    private static final int BITMAP_PIPELINE_BATCH = 500;
    private static final Duration REBUILD_TEMP_TTL = Duration.ofMinutes(10);

    private static final DefaultRedisScript<Long> REPLACE_BITMAP_SCRIPT = new DefaultRedisScript<>(
            "local currentVersion = tonumber(redis.call('get', KEYS[1]) or '-1')\n"
                    + "local incomingVersion = tonumber(ARGV[1])\n"
                    + "if incomingVersion < currentVersion then redis.call('del', KEYS[7]) return 0 end\n"
                    + "redis.call('set', KEYS[2], ARGV[3])\n"
                    + "redis.call('set', KEYS[3], ARGV[4])\n"
                    + "local currentViews = tonumber(redis.call('get', KEYS[4]) or '0')\n"
                    + "local dbViews = tonumber(ARGV[5])\n"
                    + "if currentViews < dbViews then currentViews = dbViews end\n"
                    + "redis.call('set', KEYS[4], tostring(currentViews))\n"
                    + "if ARGV[7] == '1' then redis.call('rename', KEYS[7], KEYS[5]) redis.call('persist', KEYS[5])\n"
                    + "else redis.call('del', KEYS[5]) end\n"
                    + "redis.call('zadd', KEYS[6], tonumber(ARGV[6]), ARGV[2])\n"
                    + "redis.call('set', KEYS[1], ARGV[1])\n"
                    + "return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

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
        Map<Long, CommunityCounts> result = new HashMap<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            result.put(ordered.get(i), new CommunityCounts(
                    parseLong(valueAt(values, i * 3)),
                    parseLong(valueAt(values, i * 3 + 1)),
                    parseLong(valueAt(values, i * 3 + 2))));
        }
        return result;
    }

    public void batchUpdateHotScore(Map<Long, Double> scoreById) {
        if (scoreById == null || scoreById.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConnection = (StringRedisConnection) connection;
            for (Map.Entry<Long, Double> entry : scoreById.entrySet()) {
                stringConnection.zAdd(CommunityRedisKeys.HOT_GLOBAL,
                        entry.getValue(), String.valueOf(entry.getKey()));
            }
            return null;
        });
    }

    public boolean replaceFullPostState(Long publicLogId,
                                        long cacheVersion,
                                        long likes,
                                        long comments,
                                        long views,
                                        double hotScore,
                                        Collection<Long> likedUserIds) {
        boolean hasLikes = likedUserIds != null && !likedUserIds.isEmpty();
        String tempKey = CommunityRedisKeys.bitmapRebuild(
                publicLogId, UUID.randomUUID().toString());
        try {
            if (hasLikes) {
                redisTemplate.opsForValue().set(tempKey, "", REBUILD_TEMP_TTL);
                List<Long> users = new ArrayList<>(likedUserIds);
                for (int start = 0; start < users.size(); start += BITMAP_PIPELINE_BATCH) {
                    List<Long> batch = users.subList(start,
                            Math.min(start + BITMAP_PIPELINE_BATCH, users.size()));
                    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                        StringRedisConnection stringConnection = (StringRedisConnection) connection;
                        for (Long userId : batch) {
                            // userId 直接作为 offset 会形成稀疏 Bitmap；优点是 O(1) 判定，代价是空间取决于最大 userId。
                            stringConnection.setBit(tempKey, userId, true);
                        }
                        return null;
                    });
                }
            }
            Long applied = redisTemplate.execute(REPLACE_BITMAP_SCRIPT, List.of(
                            CommunityRedisKeys.postVersion(publicLogId),
                            CommunityRedisKeys.countLike(publicLogId),
                            CommunityRedisKeys.countComment(publicLogId),
                            CommunityRedisKeys.countView(publicLogId),
                            CommunityRedisKeys.likeBitmap(publicLogId),
                            CommunityRedisKeys.HOT_GLOBAL,
                            tempKey),
                    String.valueOf(cacheVersion), String.valueOf(publicLogId),
                    String.valueOf(likes), String.valueOf(comments), String.valueOf(views),
                    String.valueOf(hotScore), hasLikes ? "1" : "0");
            return Long.valueOf(1L).equals(applied);
        } catch (RuntimeException exception) {
            try {
                redisTemplate.delete(tempKey);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    public void rebuildUserFeed(Long userId, Collection<FeedEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        String feedKey = CommunityRedisKeys.feedUser(userId);
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection stringConnection = (StringRedisConnection) connection;
            for (FeedEntry entry : entries) {
                stringConnection.zAdd(feedKey, entry.timestampSeconds(),
                        String.valueOf(entry.publicLogId()));
            }
            stringConnection.zRemRange(feedKey, 0, -CommunityRedisService.FEED_KEEP_SIZE - 1);
            stringConnection.expire(feedKey, CommunityRedisService.FEED_TTL.getSeconds());
            return null;
        });
    }

    private static String valueAt(List<String> values, int index) {
        return values == null || index >= values.size() ? null : values.get(index);
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public record CommunityCounts(long like, long comment, long view) {
    }

    public record FeedEntry(Long publicLogId, long timestampSeconds) {
    }
}
