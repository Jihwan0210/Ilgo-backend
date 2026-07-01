package com.ilgo.analysis.dto;

import com.ilgo.analysis.RiskLevel;

public record RiskDto(
        String id,
        RiskLevel level,
        String type,
        String message
) {
}
