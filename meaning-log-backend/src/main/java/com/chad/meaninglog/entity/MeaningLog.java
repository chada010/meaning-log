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
@TableName("meaning_logs")
public class MeaningLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private LocalDate logDate;

    private String mood;

    private String aiTitle;

    private String aiSummary;

    private String aiTags;

    private boolean favorite = false;

    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private UserAccount user;

    public void setUser(UserAccount user) {
        this.user = user;
        this.userId = user == null ? null : user.getId();
    }
}
