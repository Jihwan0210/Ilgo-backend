package com.ilgo.common;

import java.util.regex.Pattern;

/**
 * 로그에 남길 문자열에서 개인정보(주민번호·카드번호·긴 계좌/번호열)를 마스킹한다(§6).
 * 원문 전체는 어차피 INFO 로 남기지 않지만, 파싱 실패 시 WARN 스니펫에 원문 일부가 섞일 수 있어
 * 최소한의 마스킹을 적용한다. 금액(68,000)·짧은 전화(1577-1000, 118)·날짜는 보존한다.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    // 카드번호 4-4-4-4 (주민번호 6-7 매칭보다 먼저 처리)
    private static final Pattern CARD = Pattern.compile("\\d{4}-\\d{4}-\\d{4}-\\d{4}");
    // 주민등록번호 6-7
    private static final Pattern RRN = Pattern.compile("(\\d{6})-\\d{7}");
    // 계좌/인증 등 10자리 이상 연속 숫자열
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{10,}");

    public static String maskPii(String s) {
        if (s == null) {
            return null;
        }
        String out = CARD.matcher(s).replaceAll("****-****-****-****");
        out = RRN.matcher(out).replaceAll("$1-*******");
        out = LONG_DIGITS.matcher(out).replaceAll("**********");
        return out;
    }
}
