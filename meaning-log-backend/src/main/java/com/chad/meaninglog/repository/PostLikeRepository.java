package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.PostLike;

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
}
