package com.ilgo.auth.dto;

/** 로그아웃 요청. JSON: { refresh_token } */
public record LogoutRequest(String refreshToken) {
}
