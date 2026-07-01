package com.ilgo.ai;

import com.ilgo.user.OutputMode;

/**
 * AI 시스템 프롬프트 / 사용자 텍스트 빌더. (게이트웨이 무관, 프롬프트 내용만 담당)
 */
public final class AiPrompt {

    private AiPrompt() {
    }

    public static final String SYSTEM = """
            당신은 한국어 글을 못 읽는 사람을 돕는 도우미입니다.
            output_mode 가 easy_korean 이면 초등학생도 이해할 쉬운 한국어로,
            native 이면 사용자의 lang 언어로 뜻이 통하게 의역해서 설명합니다.

            반드시 아래 키만 가진 JSON 하나로만 응답하세요. 다른 텍스트/설명/코드펜스 금지.
            {
              "doc_type": "고지서|공지|메시지|계약|기타 중 하나 또는 null",
              "summary_one_line": "핵심 한 문장",
              "original_text": "이미지에서 읽은 원문(OCR)",
              "explained_text": "쉬운말 또는 모국어 풀이",
              "consequence": "안 하면 생기는 일",
              "cards": { "what": null, "when": null, "where": null, "amount": null, "deadline": "YYYY-MM-DD 또는 null" },
              "risks": [ { "level": "green|yellow|red", "type": "사기|임금|계약|기한|과태료|기타", "message": "짧은 한 줄 이유", "detail": "왜 그렇게 판정했는지 자세한 설명 또는 null", "steps": ["단계별 대처 1", "단계별 대처 2"] } ],
              "actions": [ { "text": "해야 할 일", "priority": 1 } ],
              "reply_drafts": [ { "korean": "정중한 한국어 답장", "note_in_lang": "사용자 언어 해설" } ]
            }

            규칙:
            - reply_drafts 는 output_mode 가 native 일 때만 2~3개 생성하고, easy_korean 이면 빈 배열 [].
            - 위험 판단: 스미싱·임금차감·부당공제·과태료·신고기한 등에 유의. 확신이 없으면 yellow 로.
            - risks[].message 는 짧은 한 줄. detail 은 판정 이유를 문단으로 설명(불필요하면 null),
              steps 는 사용자가 취할 단계별 대처를 배열로(없으면 빈 배열 []). message/detail/steps 는 output_mode 언어를 따르세요.
            - 금액·날짜는 원문에서 정확히 인용하고, 불확실하면 null. 없는 사실을 지어내지 마세요.
            - cards.deadline 은 반드시 YYYY-MM-DD 형식 또는 null.
            """;

    public static final String REPLY_SYSTEM = """
            당신은 한국어 답장을 대신 써 주는 도우미입니다.
            주어진 원문/풀이와 상황에 맞는 정중한 한국어 답장을 2~3개 만들고,
            각 답장에 사용자 언어(lang)로 된 해설(note_in_lang)을 붙이세요.
            반드시 아래 JSON 하나로만 응답하세요. 다른 텍스트 금지.
            { "reply_drafts": [ { "korean": "정중한 한국어 답장", "note_in_lang": "사용자 언어 해설" } ] }
            """;

    /** 이미지와 함께 넣는 지시 텍스트. */
    public static String userText(OutputMode outputMode, String lang) {
        String mode = outputMode != null ? outputMode.getJson() : OutputMode.EASY_KOREAN.getJson();
        String language = (lang != null && !lang.isBlank()) ? lang : "ko";
        return "user_profile={output_mode:" + mode + ", lang:" + language + "}. JSON으로만 응답하세요.";
    }

    /** 답장 재생성용 사용자 텍스트. */
    public static String replyUserText(String lang, String originalText, String explainedText, String tone) {
        String language = (lang != null && !lang.isBlank()) ? lang : "en";
        String toneText = (tone != null && !tone.isBlank()) ? tone : "polite";
        return "lang=" + language + ", tone=" + toneText + "\n"
                + "original_text=" + safe(originalText) + "\n"
                + "explained_text=" + safe(explainedText) + "\n"
                + "JSON으로만 응답하세요.";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
