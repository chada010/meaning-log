package com.chad.meaninglog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("ai_chat_messages")
public class AiChatMessage {

    public enum Role {
        USER,
        ASSISTANT
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    @TableField(exist = false)
    private AiChatSession session;

    private Role role;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public void setSession(AiChatSession session) {
        this.session = session;
        this.sessionId = session == null ? null : session.getId();
    }
}
