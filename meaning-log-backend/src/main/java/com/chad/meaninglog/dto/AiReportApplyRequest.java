package com.chad.meaninglog.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiReportApplyRequest {

    private String title;

    private String period;

    private String summary;

    private String tags;
}
