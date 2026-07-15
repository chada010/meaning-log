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
@TableName("public_logs")
public class PublicLog {

    public enum Status {
        VISIBLE,
        HIDDEN
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long logId;

    private Long userId;

    private LocalDateTime publishedAt;

    private long viewCount = 0L;

    private long likeCount = 0L;

    private long commentCount = 0L;

    private long cacheVersion = 0L;

    private String status = Status.VISIBLE.name();

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private MeaningLog log;

    @TableField(exist = false)
    private UserAccount author;
}
