package com.chad.meaninglog.service.community;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class CommunityRedisKeys {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final String HOT_GLOBAL = "community:hot:global";
    public static final String DIRTY_COUNTERS = "community:dirty:counters";

    private static final String FEED_USER_PREFIX = "community:feed:user:";
    private static final String LIKE_BITMAP_PREFIX = "community:like:bitmap:";
    private static final String COUNT_LIKE_PREFIX = "community:count:like:";
    private static final String COUNT_COMMENT_PREFIX = "community:count:comment:";
    private static final String COUNT_VIEW_PREFIX = "community:count:view:";
    private static final String FOLLOWING_PREFIX = "community:following:";
    private static final String FOLLOWER_PREFIX = "community:follower:";
    private static final String PV_HLL_PREFIX = "community:pv:";
    private static final String PUBLISH_LOCK_PREFIX = "community:lock:publish:";

    private CommunityRedisKeys() {
    }

    public static String feedUser(Long userId) {
        return FEED_USER_PREFIX + userId;
    }

    public static String likeBitmap(Long publicLogId) {
        return LIKE_BITMAP_PREFIX + publicLogId;
    }

    public static String countLike(Long publicLogId) {
        return COUNT_LIKE_PREFIX + publicLogId;
    }

    public static String countComment(Long publicLogId) {
        return COUNT_COMMENT_PREFIX + publicLogId;
    }

    public static String countView(Long publicLogId) {
        return COUNT_VIEW_PREFIX + publicLogId;
    }

    public static String following(Long userId) {
        return FOLLOWING_PREFIX + userId;
    }

    public static String follower(Long userId) {
        return FOLLOWER_PREFIX + userId;
    }

    public static String pvHll(Long publicLogId, LocalDate date) {
        return PV_HLL_PREFIX + publicLogId + ":" + date.format(DATE_FORMAT);
    }

    public static String publishLock(Long userId) {
        return PUBLISH_LOCK_PREFIX + userId;
    }
}
