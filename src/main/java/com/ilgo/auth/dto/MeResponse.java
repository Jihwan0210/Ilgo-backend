package com.ilgo.auth.dto;

import com.ilgo.user.dto.ProfileResponse;

/** 세션 확인 응답(§3-3). JSON: { user: { id, email }, profile: { ... } } */
public record MeResponse(
        AuthResponse.UserSummary user,
        ProfileResponse profile
) {
}
