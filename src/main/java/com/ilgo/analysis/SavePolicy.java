package com.ilgo.analysis;

/**
 * 분석 결과 저장 여부 결정(단일 지점).
 * 저장한다 ⇔ status == DONE AND docClass.isSavable() AND save == true.
 * 판독 실패(FAILED)·문서 아님/미지(UNKNOWN/NOT_A_DOCUMENT)·명시적 save=false 는 저장하지 않는다.
 */
public final class SavePolicy {

    private SavePolicy() {
    }

    public static boolean shouldPersist(AnalysisStatus status, DocClass docClass, boolean save) {
        return status == AnalysisStatus.DONE && docClass != null && docClass.isSavable() && save;
    }
}
