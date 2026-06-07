package com.chad.meaninglog.dto;

import com.chad.meaninglog.entity.UserAccount;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {

    private Long id;
    private String email;
    private String username;
    private String token;

    public static AuthResponse from(UserAccount user) {
        return from(user, null);
    }

    public static AuthResponse from(UserAccount user, String token) {
        return AuthResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .token(token)
                .build();
    }
}
