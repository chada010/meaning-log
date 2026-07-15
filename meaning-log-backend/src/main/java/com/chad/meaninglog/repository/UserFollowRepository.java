package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.UserFollow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends BaseMapper<UserFollow> {

    default Optional<UserFollow> findByFollowerAndFollowee(Long followerId, Long followeeId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)));
    }

    default int deleteByFollowerAndFollowee(Long followerId, Long followeeId) {
        return delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
    }

    @Insert("INSERT IGNORE INTO user_follows (follower_id, followee_id, created_at) "
            + "VALUES (#{followerId}, #{followeeId}, #{createdAt})")
    int insertIfAbsent(@Param("followerId") Long followerId,
                       @Param("followeeId") Long followeeId,
                       @Param("createdAt") LocalDateTime createdAt);

    @Select({
            "<script>",
            "SELECT followee_id FROM user_follows",
            "WHERE follower_id = #{followerId} AND followee_id IN",
            "<foreach collection='followeeIds' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    List<Long> findFolloweeIds(@Param("followerId") Long followerId,
                               @Param("followeeIds") Collection<Long> followeeIds);

    default long countFollowing(Long followerId) {
        return selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId));
    }

    default long countFollowers(Long followeeId) {
        return selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, followeeId));
    }

    default List<UserFollow> findFollowersOf(Long followeeId, int limit) {
        return selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFolloweeId, followeeId)
                .orderByDesc(UserFollow::getCreatedAt)
                .last("LIMIT " + limit));
    }

    default List<UserFollow> findFollowingOf(Long followerId, int limit) {
        return selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .orderByDesc(UserFollow::getCreatedAt)
                .last("LIMIT " + limit));
    }
}
