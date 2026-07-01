package com.ilgo.auth.dto;

/** 인증 성공 응답. JSON: { access_token, refresh_token, token_type, expires_in, user: { id, email } } */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UserSummary user
) {
    public record UserSummary(String id, String email) {
    }

    public static AuthResponse bearer(String accessToken, String refreshToken, long expiresIn,
                                      String userId, String email) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, new UserSummary(userId, email));
    }
}
