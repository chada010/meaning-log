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
@TableName("ai_chat_sessions")
public class AiChatSession {

    public enum Type {
        GENERAL,
        LOG,
        REPORT
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Type type;

    private String title;

    private Long userId;

    private Long meaningLogId;

    private Long aiReportId;

    @TableField(exist = false)
    private UserAccount user;

    @TableField(exist = false)
    private MeaningLog meaningLog;

    @TableField(exist = false)
    private AiReport aiReport;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public void setUser(UserAccount user) {
        this.user = user;
        this.userId = user == null ? null : user.getId();
    }

    public void setMeaningLog(MeaningLog meaningLog) {
        this.meaningLog = meaningLog;
        this.meaningLogId = meaningLog == null ? null : meaningLog.getId();
    }

    public void setAiReport(AiReport aiReport) {
        this.aiReport = aiReport;
        this.aiReportId = aiReport == null ? null : aiReport.getId();
    }
}
