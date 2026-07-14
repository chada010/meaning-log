package com.chad.meaninglog.dto.community;

import com.chad.meaninglog.entity.PublicLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PublishResponse {

    private Long publicLogId;
    private Long logId;
    private LocalDateTime publishedAt;

    public static PublishResponse from(PublicLog publicLog) {
        return PublishResponse.builder()
                .publicLogId(publicLog.getId())
                .logId(publicLog.getLogId())
                .publishedAt(publicLog.getPublishedAt())
                .build();
    }
}
