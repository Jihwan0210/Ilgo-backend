package com.ilgo.auth.dto;

/** 인증 성공 응답. JSON: { access_token, token_type, user: { id, email } } */
public record AuthResponse(
        String accessToken,
        String tokenType,
        UserSummary user
) {
    public record UserSummary(String id, String email) {
    }

    public static AuthResponse bearer(String token, String userId, String email) {
        return new AuthResponse(token, "Bearer", new UserSummary(userId, email));
    }
}
