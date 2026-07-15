package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.PublicLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublicLogRepository extends BaseMapper<PublicLog> {

    default Optional<PublicLog> findByLogId(Long logId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getLogId, logId)));
    }

    default Optional<PublicLog> findByLogIdForUpdate(Long logId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getLogId, logId)
                .last("FOR UPDATE")));
    }

    default Optional<PublicLog> findVisibleById(Long id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getId, id)
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())
                .apply("EXISTS (SELECT 1 FROM meaning_logs m WHERE m.id = public_logs.log_id)")));
    }

    default List<PublicLog> findLatestVisible(int offset, int size) {
        return selectList(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())
                .apply("EXISTS (SELECT 1 FROM meaning_logs m WHERE m.id = public_logs.log_id)")
                .orderByDesc(PublicLog::getPublishedAt)
                .last("LIMIT " + offset + ", " + size));
    }

    default List<PublicLog> findByUserId(Long userId, int offset, int size) {
        return selectList(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getUserId, userId)
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())
                .apply("EXISTS (SELECT 1 FROM meaning_logs m WHERE m.id = public_logs.log_id)")
                .orderByDesc(PublicLog::getPublishedAt)
                .last("LIMIT " + offset + ", " + size));
    }

    default List<PublicLog> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<PublicLog>()
                .in(PublicLog::getId, ids)
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())
                .apply("EXISTS (SELECT 1 FROM meaning_logs m WHERE m.id = public_logs.log_id)"));
    }

    default Optional<PublicLog> findVisibleByIdForUpdate(Long id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getId, id)
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())
                .apply("EXISTS (SELECT 1 FROM meaning_logs m WHERE m.id = public_logs.log_id)")
                .last("FOR UPDATE")));
    }

    @Select("SELECT p.* FROM public_logs p "
            + "INNER JOIN meaning_logs m ON m.id = p.log_id "
            + "INNER JOIN user_follows f ON f.followee_id = p.user_id "
            + "WHERE f.follower_id = #{followerId} AND p.status = 'VISIBLE' "
            + "ORDER BY p.published_at DESC LIMIT #{offset}, #{size}")
    List<PublicLog> findFollowingVisible(@Param("followerId") Long followerId,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    @Select("SELECT p.* FROM public_logs p "
            + "INNER JOIN meaning_logs m ON m.id = p.log_id "
            + "WHERE p.status = 'VISIBLE' AND p.published_at >= #{cutoff} "
            + "ORDER BY p.published_at DESC LIMIT #{limit}")
    List<PublicLog> findRecentVisible(@Param("cutoff") LocalDateTime cutoff,
                                      @Param("limit") int limit);

    default PublicLog save(PublicLog log) {
        if (log.getId() == null) {
            insert(log);
        } else {
            updateById(log);
        }
        return log;
    }

    @Update("UPDATE public_logs SET like_count = like_count + 1, "
            + "cache_version = cache_version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'VISIBLE'")
    int incrementLikeCount(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE public_logs SET like_count = GREATEST(0, like_count - 1), "
            + "cache_version = cache_version + 1, updated_at = #{now} "
            + "WHERE id = #{id}")
    int decrementLikeCount(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE public_logs SET comment_count = comment_count + 1, "
            + "cache_version = cache_version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'VISIBLE'")
    int incrementCommentCount(@Param("id") Long id, @Param("now") LocalDateTime now);
}
