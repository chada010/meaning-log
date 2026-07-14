package com.chad.meaninglog.dto.community;

import com.chad.meaninglog.entity.PostComment;
import com.chad.meaninglog.entity.UserAccount;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommentResponse {

    private Long id;
    private Long publicLogId;
    private AuthorInfo author;
    private String content;
    private Long parentId;
    private LocalDateTime createdAt;

    public static CommentResponse from(PostComment comment, UserAccount author) {
        return CommentResponse.builder()
                .id(comment.getId())
                .publicLogId(comment.getPublicLogId())
                .author(AuthorInfo.from(author))
                .content(comment.getContent())
                .parentId(comment.getParentId())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
