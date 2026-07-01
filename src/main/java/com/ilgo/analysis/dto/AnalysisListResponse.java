package com.ilgo.analysis.dto;

import java.util.List;

/** GET /v1/analyses 목록 응답. */
public record AnalysisListResponse(
        List<AnalysisListItem> items,
        String nextCursor
) {
}
