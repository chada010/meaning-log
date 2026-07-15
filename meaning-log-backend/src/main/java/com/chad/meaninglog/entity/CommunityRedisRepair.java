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
@TableName("community_redis_repairs")
public class CommunityRedisRepair {

    public enum Type {
        POST_STATE,
        POST_PUBLISH,
        FOLLOW_STATE
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    private String repairType;

    private Long aggregateId;

    private Long relatedId;

    private int attemptCount;

    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
