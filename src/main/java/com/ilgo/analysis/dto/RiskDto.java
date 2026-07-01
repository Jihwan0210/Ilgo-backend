package com.ilgo.analysis.dto;

import com.ilgo.analysis.RiskLevel;

import java.util.List;

public record RiskDto(
        String id,
        RiskLevel level,
        String type,
        String message,
        String detail,
        List<String> steps
) {
}
