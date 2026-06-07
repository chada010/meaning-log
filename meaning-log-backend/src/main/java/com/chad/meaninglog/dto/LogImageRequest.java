package com.chad.meaninglog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogImageRequest {

    private String fileName;

    private String caption;

    private String contentType;

    @NotBlank(message = "图片内容不能为空")
    private String dataUrl;
}
