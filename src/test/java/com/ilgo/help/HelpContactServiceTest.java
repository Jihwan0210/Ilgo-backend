package com.ilgo.help;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HelpContactServiceTest {

    private final HelpContactService service = new HelpContactService();

    private List<String> ids(String riskType) {
        return service.list(null, null, null, riskType).items().stream().map(HelpContact::id).toList();
    }

    @Test
    void fraudRiskType_putsKisa118First() {
        assertThat(ids("사기")).first().isEqualTo("kisa_118");
        assertThat(ids("스미싱")).first().isEqualTo("kisa_118");
        assertThat(ids("보이스피싱")).first().isEqualTo("kisa_118");
    }

    @Test
    void fraudRiskType_placesPolice112Second() {
        assertThat(ids("사기").subList(0, 2)).containsExactly("kisa_118", "police_112");
    }

    @Test
    void laborRiskType_putsMoel1350First() {
        assertThat(ids("임금")).first().isEqualTo("moel_1350");
        assertThat(ids("급여")).first().isEqualTo("moel_1350");
    }

    @Test
    void contractRiskType_includesMoelAndImmigrationFirst() {
        assertThat(ids("계약").subList(0, 2)).containsExactly("moel_1350", "immigration_1345");
        assertThat(ids("부당공제")).first().isEqualTo("moel_1350");
    }

    @Test
    void healthRiskType_putsNhisFirst() {
        assertThat(ids("건강보험")).first().isEqualTo("nhis_1577_1000");
    }

    @Test
    void unknownRiskType_returnsDefaultListWithoutError() {
        List<String> unknown = ids("완전히 모르는 값");
        List<String> none = ids(null);
        assertThat(unknown).isEqualTo(none);
        assertThat(none).containsExactlyInAnyOrder(
                "kisa_118", "moel_1350", "immigration_1345", "nhis_1577_1000", "police_112");
    }

    @Test
    void everyContactHasPhone_soSerializationNeverDrops() {
        assertThat(service.list(null, null, null, null).items())
                .allSatisfy(c -> assertThat(c.phone()).isNotBlank());
    }
}
