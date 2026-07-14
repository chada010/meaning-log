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
@TableName("notifications")
public class Notification {

    public enum Type {
        LIKE,
        COMMENT,
        FOLLOW
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long receiverId;

    private Long actorId;

    private String type;

    private Long publicLogId;

    private Long commentId;

    private String content;

    @TableField("is_read")
    private boolean read = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private UserAccount actor;
}
