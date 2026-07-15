package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.dto.community.CommunityCountRow;
import com.chad.meaninglog.entity.PostComment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
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

    @Select({
            "<script>",
            "SELECT public_log_id AS publicLogId, COUNT(*) AS total",
            "FROM post_comments WHERE public_log_id IN",
            "<foreach collection='publicLogIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "GROUP BY public_log_id",
            "</script>"
    })
    List<CommunityCountRow> countByPublicLogIds(
            @Param("publicLogIds") Collection<Long> publicLogIds);

    default int deleteByPublicLogId(Long publicLogId) {
        return delete(new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getPublicLogId, publicLogId));
    }
}
