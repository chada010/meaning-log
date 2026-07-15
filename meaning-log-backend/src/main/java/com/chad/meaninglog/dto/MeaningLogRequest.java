package com.chad.meaninglog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class MeaningLogRequest {

    @Size(max = 100, message = "标题不能超过100个字符")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 20000, message = "内容不能超过 2 万字")
    private String content;

    @NotNull(message = "日期不能为空")
    private LocalDate logDate;

    @Size(max = 30, message = "心情不能超过30个字符")
    private String mood;

    private Boolean favorite;

    @Valid
    @Size(max = 3, message = "最多上传3张图片")
    private List<@NotNull(message = "图片信息不能为空") LogImageRequest> images;
}
