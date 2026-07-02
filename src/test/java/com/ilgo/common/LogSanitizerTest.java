package com.ilgo.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void masksResidentRegistrationNumber() {
        String out = LogSanitizer.maskPii("주민번호 901201-1234567 입니다");
        assertThat(out).contains("901201-*******").doesNotContain("1234567");
    }

    @Test
    void masksCardNumber() {
        String out = LogSanitizer.maskPii("카드 1234-5678-9012-3456 결제");
        assertThat(out).doesNotContain("5678").contains("****-****-****-****");
    }

    @Test
    void masksLongAccountLikeDigitRun() {
        String out = LogSanitizer.maskPii("계좌 1002123456789 로 입금");
        assertThat(out).doesNotContain("1002123456789");
    }

    @Test
    void keepsShortNumbersLikeAmountsAndPhones() {
        String out = LogSanitizer.maskPii("미납 68,000원, 8월 15일까지, 문의 1577-1000, 118");
        assertThat(out).contains("68,000").contains("1577-1000").contains("118");
    }

    @Test
    void nullStaysNull() {
        assertThat(LogSanitizer.maskPii(null)).isNull();
    }
}
