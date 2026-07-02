package com.ilgo.analysis;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 문서 분류. AI 의 doc_type(한글 free-form)을 단일 지점에서 이 enum 으로 매핑한다.
 * 매핑 실패·미지원·null 은 UNKNOWN 으로 수렴한다. 저장 정책 분기에 사용한다({@link SavePolicy}).
 * wire 값은 소문자 스네이크(notice|announcement|message|contract|unknown|not_a_document).
 */
public enum DocClass {
    NOTICE("notice"),
    ANNOUNCEMENT("announcement"),
    MESSAGE("message"),
    CONTRACT("contract"),
    UNKNOWN("unknown"),
    NOT_A_DOCUMENT("not_a_document");

    private final String json;

    DocClass(String json) {
        this.json = json;
    }

    @JsonValue
    public String getJson() {
        return json;
    }

    /** 저장 대상 여부. 실제 문서 분류만 저장한다. */
    public boolean isSavable() {
        return this == NOTICE || this == ANNOUNCEMENT || this == MESSAGE || this == CONTRACT;
    }

    /** AI doc_type(한글/영문 free-form) → DocClass 단일 매핑. 미지/null → UNKNOWN. */
    public static DocClass from(String docType) {
        if (docType == null) {
            return UNKNOWN;
        }
        String s = docType.trim();
        if (s.isEmpty()) {
            return UNKNOWN;
        }
        String lower = s.toLowerCase();

        if (s.contains("고지서")) {
            return NOTICE;
        }
        if (s.contains("공지")) {
            return ANNOUNCEMENT;
        }
        if (s.contains("메시지") || s.contains("문자") || s.contains("카톡") || lower.contains("message")) {
            return MESSAGE;
        }
        if (s.contains("계약") || lower.contains("contract")) {
            return CONTRACT;
        }
        if (s.contains("문서아님") || s.contains("문서 아님") || lower.contains("not_a_document")
                || lower.contains("not a document")) {
            return NOT_A_DOCUMENT;
        }
        return UNKNOWN;
    }
}
