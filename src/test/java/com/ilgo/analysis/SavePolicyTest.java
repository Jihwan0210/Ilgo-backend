package com.ilgo.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SavePolicyTest {

    @Test
    void persists_whenDoneAndSavableAndSaveTrue() {
        assertThat(SavePolicy.shouldPersist(AnalysisStatus.DONE, DocClass.NOTICE, true)).isTrue();
        assertThat(SavePolicy.shouldPersist(AnalysisStatus.DONE, DocClass.MESSAGE, true)).isTrue();
        assertThat(SavePolicy.shouldPersist(AnalysisStatus.DONE, DocClass.CONTRACT, true)).isTrue();
    }

    @Test
    void skips_whenStatusNotDone() {
        assertThat(SavePolicy.shouldPersist(AnalysisStatus.FAILED, DocClass.NOTICE, true)).isFalse();
        assertThat(SavePolicy.shouldPersist(AnalysisStatus.PROCESSING, DocClass.NOTICE, true)).isFalse();
    }

    @Test
    void skips_whenDocClassNotSavable() {
        assertThat(SavePolicy.shouldPersist(AnalysisStatus.DONE, DocClass.UNKNOWN, true)).isFalse();
        assertThat(SavePolicy.shouldPersist(AnalysisStatus.DONE, DocClass.NOT_A_DOCUMENT, true)).isFalse();
    }

    @Test
    void skips_whenSaveFalse() {
        assertThat(SavePolicy.shouldPersist(AnalysisStatus.DONE, DocClass.NOTICE, false)).isFalse();
    }
}
