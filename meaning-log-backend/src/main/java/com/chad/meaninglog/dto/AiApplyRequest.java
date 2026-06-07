package com.chad.meaninglog.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AiApplyRequest {

    private String title;

    private String summary;

    private List<String> tags;
}
