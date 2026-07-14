package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.UserFollow;

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
