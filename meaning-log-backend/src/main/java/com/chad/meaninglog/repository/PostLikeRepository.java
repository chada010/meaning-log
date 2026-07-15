package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.dto.community.CommunityCountRow;
import com.chad.meaninglog.entity.PostLike;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends BaseMapper<PostLike> {

    default Optional<PostLike> findByPublicLogIdAndUserId(Long publicLogId, Long userId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPublicLogId, publicLogId)
                .eq(PostLike::getUserId, userId)));
    }

    default long countByPublicLogId(Long publicLogId) {
        return selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPublicLogId, publicLogId));
    }

    @Insert("INSERT IGNORE INTO post_likes (public_log_id, user_id, created_at) "
            + "VALUES (#{publicLogId}, #{userId}, #{createdAt})")
    int insertIfAbsent(@Param("publicLogId") Long publicLogId,
                       @Param("userId") Long userId,
                       @Param("createdAt") LocalDateTime createdAt);

    @Select({
            "<script>",
            "SELECT public_log_id AS publicLogId, COUNT(*) AS total",
            "FROM post_likes WHERE public_log_id IN",
            "<foreach collection='publicLogIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "GROUP BY public_log_id",
            "</script>"
    })
    List<CommunityCountRow> countByPublicLogIds(
            @Param("publicLogIds") Collection<Long> publicLogIds);

    @Select({
            "<script>",
            "SELECT public_log_id FROM post_likes",
            "WHERE user_id = #{userId} AND public_log_id IN",
            "<foreach collection='publicLogIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    List<Long> findLikedPublicLogIds(@Param("userId") Long userId,
                                     @Param("publicLogIds") Collection<Long> publicLogIds);

    default List<PostLike> findByPublicLogIds(Collection<Long> publicLogIds) {
        if (publicLogIds == null || publicLogIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<PostLike>()
                .in(PostLike::getPublicLogId, publicLogIds));
    }

    default int deleteByPublicLogIdAndUserId(Long publicLogId, Long userId) {
        return delete(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPublicLogId, publicLogId)
                .eq(PostLike::getUserId, userId));
    }

    default int deleteByPublicLogId(Long publicLogId) {
        return delete(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPublicLogId, publicLogId));
    }
}
