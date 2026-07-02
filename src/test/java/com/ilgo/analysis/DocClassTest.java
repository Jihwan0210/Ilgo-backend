package com.ilgo.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocClassTest {

    @Test
    void from_mapsKnownDocumentTypes() {
        assertThat(DocClass.from("고지서")).isEqualTo(DocClass.NOTICE);
        assertThat(DocClass.from("공지")).isEqualTo(DocClass.ANNOUNCEMENT);
        assertThat(DocClass.from("메시지")).isEqualTo(DocClass.MESSAGE);
        assertThat(DocClass.from("문자")).isEqualTo(DocClass.MESSAGE);
        assertThat(DocClass.from("카톡")).isEqualTo(DocClass.MESSAGE);
        assertThat(DocClass.from("계약")).isEqualTo(DocClass.CONTRACT);
    }

    @Test
    void from_mapsNotADocumentSignals() {
        assertThat(DocClass.from("문서아님")).isEqualTo(DocClass.NOT_A_DOCUMENT);
        assertThat(DocClass.from("not_a_document")).isEqualTo(DocClass.NOT_A_DOCUMENT);
    }

    @Test
    void from_unknownAndBlankAndNullBecomeUnknown() {
        assertThat(DocClass.from("기타")).isEqualTo(DocClass.UNKNOWN);
        assertThat(DocClass.from("알 수 없음")).isEqualTo(DocClass.UNKNOWN);
        assertThat(DocClass.from("알수없음")).isEqualTo(DocClass.UNKNOWN);
        assertThat(DocClass.from("unknown")).isEqualTo(DocClass.UNKNOWN);
        assertThat(DocClass.from("other")).isEqualTo(DocClass.UNKNOWN);
        assertThat(DocClass.from(null)).isEqualTo(DocClass.UNKNOWN);
        assertThat(DocClass.from("")).isEqualTo(DocClass.UNKNOWN);
        assertThat(DocClass.from("   ")).isEqualTo(DocClass.UNKNOWN);
        assertThat(DocClass.from("셀카사진")).isEqualTo(DocClass.UNKNOWN);
    }

    @Test
    void isSavable_onlyForRealDocumentClasses() {
        assertThat(DocClass.NOTICE.isSavable()).isTrue();
        assertThat(DocClass.ANNOUNCEMENT.isSavable()).isTrue();
        assertThat(DocClass.MESSAGE.isSavable()).isTrue();
        assertThat(DocClass.CONTRACT.isSavable()).isTrue();
        assertThat(DocClass.UNKNOWN.isSavable()).isFalse();
        assertThat(DocClass.NOT_A_DOCUMENT.isSavable()).isFalse();
    }

    @Test
    void jsonValue_isLowercaseWire() {
        assertThat(DocClass.NOTICE.getJson()).isEqualTo("notice");
        assertThat(DocClass.NOT_A_DOCUMENT.getJson()).isEqualTo("not_a_document");
    }
}
