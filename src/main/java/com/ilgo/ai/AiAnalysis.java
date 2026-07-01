package com.ilgo.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * AI 가 반환한 JSON 을 매핑한 구조체. (snake_case 는 전역 네이밍 전략으로 매핑)
 * 서비스가 이 값을 엔티티로 변환한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAnalysis(
        String docType,
        String summaryOneLine,
        String originalText,
        String explainedText,
        String consequence,
        Cards cards,
        List<Risk> risks,
        List<Action> actions,
        List<ReplyDraft> replyDrafts
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cards(String what, String when, String where, String amount, String deadline) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Risk(String level, String type, String message, String detail, List<String> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Action(String text, Integer priority) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReplyDraft(String korean, String noteInLang) {
    }
}
