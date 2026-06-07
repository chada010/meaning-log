package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeaningLogRepository extends BaseMapper<MeaningLog> {

    default List<MeaningLog> findByUserOrderByCreatedAtDesc(UserAccount user) {
        return selectList(baseUserQuery(user).orderByDesc(MeaningLog::getCreatedAt));
    }

    default List<MeaningLog> findByUserOrderByLogDateDescCreatedAtDesc(UserAccount user) {
        return selectList(baseUserQuery(user)
                .orderByDesc(MeaningLog::getLogDate)
                .orderByDesc(MeaningLog::getCreatedAt));
    }

    default List<MeaningLog> findByUserAndLogDateOrderByCreatedAtDesc(UserAccount user, LocalDate logDate) {
        return selectList(baseUserQuery(user)
                .eq(MeaningLog::getLogDate, logDate)
                .orderByDesc(MeaningLog::getCreatedAt));
    }

    default List<MeaningLog> findByUserAndLogDateBetweenOrderByLogDateDesc(
            UserAccount user,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return selectList(baseUserQuery(user)
                .between(MeaningLog::getLogDate, startDate, endDate)
                .orderByDesc(MeaningLog::getLogDate)
                .orderByDesc(MeaningLog::getCreatedAt));
    }

    default Optional<MeaningLog> findByIdAndUser(Long id, UserAccount user) {
        return Optional.ofNullable(selectOne(baseUserQuery(user).eq(MeaningLog::getId, id)));
    }

    default MeaningLog save(MeaningLog log) {
        if (log.getId() == null) {
            insert(log);
        } else {
            updateById(log);
        }
        return log;
    }

    default void delete(MeaningLog log) {
        deleteById(log.getId());
    }

    private LambdaQueryWrapper<MeaningLog> baseUserQuery(UserAccount user) {
        return new LambdaQueryWrapper<MeaningLog>().eq(MeaningLog::getUserId, user.getId());
    }
}
