package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.AiChatSession;
import com.chad.meaninglog.entity.AiReport;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;

import java.util.List;
import java.util.Optional;

public interface AiChatSessionRepository extends BaseMapper<AiChatSession> {

    default List<AiChatSession> findByUserAndTypeOrderByUpdatedAtDesc(UserAccount user, AiChatSession.Type type) {
        return selectList(baseUserQuery(user)
                .eq(AiChatSession::getType, type)
                .orderByDesc(AiChatSession::getUpdatedAt));
    }

    default Optional<AiChatSession> findByIdAndUser(Long id, UserAccount user) {
        return Optional.ofNullable(selectOne(baseUserQuery(user).eq(AiChatSession::getId, id)));
    }

    default Optional<AiChatSession> findFirstByUserAndTypeAndMeaningLogOrderByUpdatedAtDesc(
            UserAccount user,
            AiChatSession.Type type,
            MeaningLog meaningLog
    ) {
        return selectList(baseUserQuery(user)
                .eq(AiChatSession::getType, type)
                .eq(AiChatSession::getMeaningLogId, meaningLog.getId())
                .orderByDesc(AiChatSession::getUpdatedAt)
                .last("limit 1"))
                .stream()
                .findFirst();
    }

    default Optional<AiChatSession> findFirstByUserAndTypeAndAiReportOrderByUpdatedAtDesc(
            UserAccount user,
            AiChatSession.Type type,
            AiReport aiReport
    ) {
        return selectList(baseUserQuery(user)
                .eq(AiChatSession::getType, type)
                .eq(AiChatSession::getAiReportId, aiReport.getId())
                .orderByDesc(AiChatSession::getUpdatedAt)
                .last("limit 1"))
                .stream()
                .findFirst();
    }

    default AiChatSession save(AiChatSession session) {
        if (session.getId() == null) {
            insert(session);
        } else {
            updateById(session);
        }
        return session;
    }

    default void deleteByMeaningLog(MeaningLog meaningLog) {
        delete(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getMeaningLogId, meaningLog.getId()));
    }

    private LambdaQueryWrapper<AiChatSession> baseUserQuery(UserAccount user) {
        return new LambdaQueryWrapper<AiChatSession>().eq(AiChatSession::getUserId, user.getId());
    }
}
