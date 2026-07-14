package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.PostComment;

import java.util.List;

public interface PostCommentRepository extends BaseMapper<PostComment> {

    default List<PostComment> findByPublicLogId(Long publicLogId, int offset, int size) {
        return selectList(new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getPublicLogId, publicLogId)
                .orderByDesc(PostComment::getCreatedAt)
                .last("LIMIT " + offset + ", " + size));
    }

    default long countByPublicLogId(Long publicLogId) {
        return selectCount(new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getPublicLogId, publicLogId));
    }
}
