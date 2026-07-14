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
@TableName("user_follows")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long followerId;

    private Long followeeId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
