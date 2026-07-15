package com.chad.meaninglog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogImageRequest {

    @Size(max = 180, message = "图片文件名不能超过180个字符")
    private String fileName;

    @Size(max = 160, message = "图片说明不能超过160个字符")
    private String caption;

    @Size(max = 80, message = "图片类型不能超过80个字符")
    private String contentType;

    @NotBlank(message = "图片内容不能为空")
    @Size(max = 2900000, message = "图片内容过大")
    private String dataUrl;
}
