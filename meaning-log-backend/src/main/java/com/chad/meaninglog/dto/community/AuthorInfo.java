package com.chad.meaninglog.dto.community;

import com.chad.meaninglog.entity.UserAccount;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthorInfo {

    private Long id;
    private String username;

    public static AuthorInfo from(UserAccount user) {
        if (user == null) {
            return null;
        }
        return AuthorInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .build();
    }
}
