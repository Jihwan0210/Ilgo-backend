package com.ilgo.user.dto;

import com.ilgo.user.OutputMode;

/**
 * 프로필 부분 수정 요청(§4). 모든 필드 선택 — null 이면 기존 값을 유지한다.
 */
public record ProfileUpdateRequest(
        OutputMode outputMode,
        String lang,
        Double textScale,
        Boolean deadlineAlarm
) {
}
