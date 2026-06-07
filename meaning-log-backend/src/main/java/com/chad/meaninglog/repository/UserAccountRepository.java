package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.UserAccount;

import java.util.Optional;

public interface UserAccountRepository extends BaseMapper<UserAccount> {

    default boolean existsByEmail(String email) {
        return exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getEmail, email));
    }

    default boolean existsByUsername(String username) {
        return exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
    }

    default Optional<UserAccount> findByEmail(String email) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getEmail, email)));
    }

    default UserAccount save(UserAccount user) {
        if (user.getId() == null) {
            insert(user);
        } else {
            updateById(user);
        }
        return user;
    }
}
