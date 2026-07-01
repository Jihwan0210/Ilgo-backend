package com.ilgo.analysis.dto;

import java.time.LocalDate;

/** 핵심 정리 카드(1:1). 미해당 항목은 null. */
public record CardDto(
        String what,
        String when,
        String where,
        String amount,
        LocalDate deadline
) {
}
