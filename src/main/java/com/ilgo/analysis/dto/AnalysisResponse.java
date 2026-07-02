package com.ilgo.analysis.dto;

import com.ilgo.analysis.AnalysisStatus;
import com.ilgo.analysis.DocClass;
import com.ilgo.user.OutputMode;

import java.time.OffsetDateTime;
import java.util.List;

/** POST /v1/analyses, GET /v1/analyses/{id} 응답 (전체 객체). */
public record AnalysisResponse(
        String id,
        AnalysisStatus status,
        OutputMode outputMode,
        String lang,
        String docType,
        DocClass docClass,
        String summaryOneLine,
        String originalText,
        String explainedText,
        String consequence,
        CardDto cards,
        List<RiskDto> risks,
        List<ActionDto> actions,
        List<ReplyDraftDto> replyDrafts,
        OffsetDateTime createdAt
) {
    /** docClass 미지정 시 docType 에서 파생(단일 파생점). */
    public AnalysisResponse {
        if (docClass == null) {
            docClass = DocClass.from(docType);
        }
    }
}
