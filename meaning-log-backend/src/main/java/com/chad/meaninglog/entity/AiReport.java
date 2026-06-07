package com.chad.meaninglog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("ai_reports")
public class AiReport {

    public enum Type {
        DAILY,
        WEEKLY,
        MONTHLY,
        CUSTOM
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Type type;

    private String title;

    private String period;

    private LocalDate startDate;

    private LocalDate endDate;

    private String summary;

    private String tags;

    private Long userId;

    @TableField(exist = false)
    private UserAccount user;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public void setUser(UserAccount user) {
        this.user = user;
        this.userId = user == null ? null : user.getId();
    }
}
