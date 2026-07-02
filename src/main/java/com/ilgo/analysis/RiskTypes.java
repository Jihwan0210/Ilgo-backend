package com.ilgo.analysis;

/**
 * 위험 유형(risk.type) 정규화 + 분류 유틸.
 *
 * <p>AI 가 내보내는 자유 문자열을 표준 값(canonical)으로 정규화하고,
 * help-contacts 정렬용 계열(family)로 분류한다. DB 는 varchar 이므로 저장 제약은 아니지만,
 * §10 프론트 계약(risk.type contains 사기/스미싱 → 118 등)이 안정적으로 성립하도록
 * 서버에서 표준값으로 모아 준다. 알 수 없는 값만 "기타" 로 떨어뜨린다.
 */
public final class RiskTypes {

    private RiskTypes() {
    }

    /** help-contacts 정렬을 위한 위험 계열. */
    public enum Family {
        FRAUD, LABOR, CONTRACT, HEALTH, FINE, OTHER
    }

    private static final String GITA = "기타";

    /**
     * 자유 문자열을 표준 위험 유형으로 정규화한다.
     * 표준값: 사기 | 스미싱 | 임금 | 계약 | 부당공제 | 기한 | 과태료 | 건강보험 | 기타.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return GITA;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return GITA;
        }
        String lower = s.toLowerCase();

        // 사기 계열 (스미싱은 별도 표준값으로 보존)
        if (s.contains("스미싱") || lower.contains("smishing")) {
            return "스미싱";
        }
        if (s.contains("사기") || s.contains("피싱") || s.contains("사칭")
                || lower.contains("phishing") || lower.contains("scam") || lower.contains("fraud")) {
            return "사기";
        }
        // 임금 계열 (부당공제는 별도 표준값 — 계약의 "공제" 매칭보다 먼저 확인)
        if (s.contains("부당공제") || s.contains("부당 공제")) {
            return "부당공제";
        }
        if (s.contains("임금") || s.contains("급여") || s.contains("일당") || s.contains("체불")) {
            return "임금";
        }
        // 계약 계열
        if (s.contains("계약") || s.contains("숙소") || s.contains("공제")) {
            return "계약";
        }
        // 건강보험
        if (s.contains("건강보험") || s.contains("보험료")) {
            return "건강보험";
        }
        // 과태료 / 벌금
        if (s.contains("과태료") || s.contains("벌금")) {
            return "과태료";
        }
        // 납부/신고 기한
        if (s.contains("기한")) {
            return "기한";
        }
        return GITA;
    }

    /** 정규화 후 계열로 분류한다. help-contacts 정렬에 사용. */
    public static Family family(String raw) {
        String t = normalize(raw);
        return switch (t) {
            case "사기", "스미싱" -> Family.FRAUD;
            case "임금", "부당공제" -> Family.LABOR;
            case "계약" -> Family.CONTRACT;
            case "건강보험" -> Family.HEALTH;
            case "과태료", "기한" -> Family.FINE;
            default -> Family.OTHER;
        };
    }
}
