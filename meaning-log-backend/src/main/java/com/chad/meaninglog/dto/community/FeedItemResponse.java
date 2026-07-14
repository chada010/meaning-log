package com.chad.meaninglog.dto.community;

import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.PublicLog;
import com.chad.meaninglog.entity.UserAccount;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class FeedItemResponse {

    private Long publicLogId;
    private Long logId;
    private AuthorInfo author;
    private String title;
    private String aiTitle;
    private String aiSummary;
    private String aiTags;
    private LocalDate logDate;
    private long likeCount;
    private long commentCount;
    private long viewCount;
    private boolean liked;
    private boolean followingAuthor;
    private LocalDateTime publishedAt;

    public static FeedItemResponse from(PublicLog publicLog,
                                        MeaningLog log,
                                        UserAccount author,
                                        long likes,
                                        long comments,
                                        long views,
                                        boolean liked,
                                        boolean followingAuthor) {
        return FeedItemResponse.builder()
                .publicLogId(publicLog.getId())
                .logId(publicLog.getLogId())
                .author(AuthorInfo.from(author))
                .title(log == null ? null : log.getTitle())
                .aiTitle(log == null ? null : log.getAiTitle())
                .aiSummary(log == null ? null : log.getAiSummary())
                .aiTags(log == null ? null : log.getAiTags())
                .logDate(log == null ? null : log.getLogDate())
                .likeCount(likes)
                .commentCount(comments)
                .viewCount(views)
                .liked(liked)
                .followingAuthor(followingAuthor)
                .publishedAt(publicLog.getPublishedAt())
                .build();
    }
}
