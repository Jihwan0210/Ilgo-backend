package com.ilgo.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskTypesTest {

    @Test
    void normalize_keepsCanonicalValues() {
        assertThat(RiskTypes.normalize("사기")).isEqualTo("사기");
        assertThat(RiskTypes.normalize("스미싱")).isEqualTo("스미싱");
        assertThat(RiskTypes.normalize("임금")).isEqualTo("임금");
        assertThat(RiskTypes.normalize("계약")).isEqualTo("계약");
        assertThat(RiskTypes.normalize("부당공제")).isEqualTo("부당공제");
        assertThat(RiskTypes.normalize("기한")).isEqualTo("기한");
        assertThat(RiskTypes.normalize("과태료")).isEqualTo("과태료");
        assertThat(RiskTypes.normalize("건강보험")).isEqualTo("건강보험");
        assertThat(RiskTypes.normalize("기타")).isEqualTo("기타");
    }

    @Test
    void normalize_mapsFraudFamilyToSagi() {
        assertThat(RiskTypes.normalize("피싱")).isEqualTo("사기");
        assertThat(RiskTypes.normalize("보이스피싱")).isEqualTo("사기");
        assertThat(RiskTypes.normalize("사칭")).isEqualTo("사기");
        assertThat(RiskTypes.normalize("phishing")).isEqualTo("사기");
        assertThat(RiskTypes.normalize("SMISHING")).isEqualTo("스미싱");
    }

    @Test
    void normalize_mapsLaborFamilyToImgeum() {
        assertThat(RiskTypes.normalize("급여")).isEqualTo("임금");
        assertThat(RiskTypes.normalize("일당")).isEqualTo("임금");
        assertThat(RiskTypes.normalize("체불")).isEqualTo("임금");
    }

    @Test
    void normalize_distinguishesBudangGongjeFromGyeyak() {
        assertThat(RiskTypes.normalize("부당공제")).isEqualTo("부당공제");
        assertThat(RiskTypes.normalize("숙소비 공제")).isEqualTo("계약");
    }

    @Test
    void normalize_unknownAndBlankBecomeGita() {
        assertThat(RiskTypes.normalize(null)).isEqualTo("기타");
        assertThat(RiskTypes.normalize("")).isEqualTo("기타");
        assertThat(RiskTypes.normalize("   ")).isEqualTo("기타");
        assertThat(RiskTypes.normalize("완전히 모르는 값")).isEqualTo("기타");
    }

    @Test
    void family_classifiesRawAndCanonicalValues() {
        assertThat(RiskTypes.family("사기")).isEqualTo(RiskTypes.Family.FRAUD);
        assertThat(RiskTypes.family("보이스피싱")).isEqualTo(RiskTypes.Family.FRAUD);
        assertThat(RiskTypes.family("스미싱")).isEqualTo(RiskTypes.Family.FRAUD);
        assertThat(RiskTypes.family("임금")).isEqualTo(RiskTypes.Family.LABOR);
        assertThat(RiskTypes.family("부당공제")).isEqualTo(RiskTypes.Family.LABOR);
        assertThat(RiskTypes.family("계약")).isEqualTo(RiskTypes.Family.CONTRACT);
        assertThat(RiskTypes.family("건강보험")).isEqualTo(RiskTypes.Family.HEALTH);
        assertThat(RiskTypes.family("과태료")).isEqualTo(RiskTypes.Family.FINE);
        assertThat(RiskTypes.family("기한")).isEqualTo(RiskTypes.Family.FINE);
        assertThat(RiskTypes.family(null)).isEqualTo(RiskTypes.Family.OTHER);
        assertThat(RiskTypes.family("아무거나")).isEqualTo(RiskTypes.Family.OTHER);
    }
}
