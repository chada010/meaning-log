package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.AiChatMessage;
import com.chad.meaninglog.entity.AiChatSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AiChatMessageRepository extends BaseMapper<AiChatMessage> {

    @Delete("""
            DELETE FROM ai_chat_messages
            WHERE session_id IN (
                SELECT id FROM ai_chat_sessions WHERE meaning_log_id = #{meaningLogId}
            )
            """)
    int deleteByMeaningLogId(@Param("meaningLogId") Long meaningLogId);

    default List<AiChatMessage> findBySessionOrderByCreatedAtAsc(AiChatSession session) {
        return selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, session.getId())
                .orderByAsc(AiChatMessage::getCreatedAt));
    }

    default List<AiChatMessage> findTop16BySessionOrderByCreatedAtDesc(AiChatSession session) {
        return selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, session.getId())
                .orderByDesc(AiChatMessage::getCreatedAt)
                .last("limit 16"));
    }

    default AiChatMessage save(AiChatMessage message) {
        if (message.getId() == null) {
            insert(message);
        } else {
            updateById(message);
        }
        return message;
    }
}
