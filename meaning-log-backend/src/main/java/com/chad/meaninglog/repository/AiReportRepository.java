package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.UserAccount;

import java.util.List;
import java.util.Optional;

public interface AiReportRepository extends BaseMapper<AiReport> {

    default List<AiReport> findByUserOrderByCreatedAtDesc(UserAccount user) {
        return selectList(baseUserQuery(user).orderByDesc(AiReport::getCreatedAt));
    }

    default Optional<AiReport> findByIdAndUser(Long id, UserAccount user) {
        return Optional.ofNullable(selectOne(baseUserQuery(user).eq(AiReport::getId, id)));
    }

    default AiReport save(AiReport report) {
        if (report.getId() == null) {
            insert(report);
        } else {
            updateById(report);
        }
        return report;
    }

    private LambdaQueryWrapper<AiReport> baseUserQuery(UserAccount user) {
        return new LambdaQueryWrapper<AiReport>().eq(AiReport::getUserId, user.getId());
    }
}
