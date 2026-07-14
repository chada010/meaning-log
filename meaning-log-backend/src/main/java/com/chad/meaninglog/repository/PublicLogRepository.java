package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.PublicLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PublicLogRepository extends BaseMapper<PublicLog> {

    default Optional<PublicLog> findByLogId(Long logId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getLogId, logId)));
    }

    default Optional<PublicLog> findVisibleById(Long id) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getId, id)
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())));
    }

    default List<PublicLog> findLatestVisible(int offset, int size) {
        return selectList(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())
                .orderByDesc(PublicLog::getPublishedAt)
                .last("LIMIT " + offset + ", " + size));
    }

    default List<PublicLog> findByUserId(Long userId, int offset, int size) {
        return selectList(new LambdaQueryWrapper<PublicLog>()
                .eq(PublicLog::getUserId, userId)
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name())
                .orderByDesc(PublicLog::getPublishedAt)
                .last("LIMIT " + offset + ", " + size));
    }

    default List<PublicLog> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<PublicLog>()
                .in(PublicLog::getId, ids)
                .eq(PublicLog::getStatus, PublicLog.Status.VISIBLE.name()));
    }

    default PublicLog save(PublicLog log) {
        if (log.getId() == null) {
            insert(log);
        } else {
            updateById(log);
        }
        return log;
    }

    @Update("UPDATE public_logs SET like_count = #{likeCount}, comment_count = #{commentCount}, "
            + "view_count = #{viewCount}, updated_at = NOW() WHERE id = #{id}")
    int updateCounters(@Param("id") Long id,
                       @Param("likeCount") long likeCount,
                       @Param("commentCount") long commentCount,
                       @Param("viewCount") long viewCount);
}
