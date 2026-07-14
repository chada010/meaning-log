package com.chad.meaninglog.dto.community;

import com.chad.meaninglog.entity.UserAccount;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {

    private AuthorInfo user;
    private long followerCount;
    private long followingCount;
    private long postCount;
    private boolean following;
    private boolean self;

    public static UserProfileResponse from(UserAccount user,
                                           long followerCount,
                                           long followingCount,
                                           long postCount,
                                           boolean following,
                                           boolean self) {
        return UserProfileResponse.builder()
                .user(AuthorInfo.from(user))
                .followerCount(followerCount)
                .followingCount(followingCount)
                .postCount(postCount)
                .following(following)
                .self(self)
                .build();
    }
}
