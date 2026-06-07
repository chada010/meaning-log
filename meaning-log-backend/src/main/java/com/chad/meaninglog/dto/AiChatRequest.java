package com.chad.meaninglog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiChatRequest {

    private Long sessionId;

    @NotBlank(message = "对话内容不能为空")
    private String message;
}
