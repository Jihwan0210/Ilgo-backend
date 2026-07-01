package com.ilgo.analysis.dto;

import com.ilgo.analysis.RiskLevel;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** GET /v1/analyses 목록 요약 항목. */
public record AnalysisListItem(
        String id,
        String docType,
        String summaryOneLine,
        RiskLevel topRiskLevel,
        LocalDate cardDeadline,
        OffsetDateTime createdAt
) {
}
