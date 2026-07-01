package com.ilgo.auth.dto;

/** 토큰 갱신 요청. JSON: { refresh_token } */
public record RefreshRequest(String refreshToken) {
}
