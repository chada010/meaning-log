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
@TableName("log_images")
public class LogImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long meaningLogId;

    @TableField(exist = false)
    private MeaningLog meaningLog;

    private String fileName;

    private String caption;

    private String contentType;

    private long fileSize;

    private int displayOrder;

    private byte[] data;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public void setMeaningLog(MeaningLog meaningLog) {
        this.meaningLog = meaningLog;
        this.meaningLogId = meaningLog == null ? null : meaningLog.getId();
    }
}
