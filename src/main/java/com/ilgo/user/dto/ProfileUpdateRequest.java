package com.ilgo.user.dto;

import com.ilgo.user.OutputMode;
import jakarta.validation.constraints.Size;

/**
 * 프로필 부분 수정 요청(§4). 모든 필드 선택 — null 이면 기존 값을 유지한다.
 * 값이 주어지면 저장 제약(예: default_lang length=8)을 넘지 않도록 검증한다.
 */
public record ProfileUpdateRequest(
        OutputMode outputMode,
        @Size(max = 8, message = "lang 은 8자 이하여야 합니다.") String lang,
        Double textScale,
        Boolean deadlineAlarm
) {
}
