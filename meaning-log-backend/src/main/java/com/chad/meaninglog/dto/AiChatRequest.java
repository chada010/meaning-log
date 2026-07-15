package com.chad.meaninglog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiChatRequest {

    private Long sessionId;

    @NotBlank(message = "对话内容不能为空")
    @Size(max = 600, message = "对话内容不能超过600个字符")
    private String message;
}
