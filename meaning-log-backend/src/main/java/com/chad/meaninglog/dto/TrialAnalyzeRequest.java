package com.chad.meaninglog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TrialAnalyzeRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题不能超过100个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 20000, message = "内容不能超过 2 万字")
    private String content;

    @NotNull(message = "日期不能为空")
    private LocalDate logDate;

    @Size(max = 30, message = "心情不能超过30个字符")
    private String mood;
}
