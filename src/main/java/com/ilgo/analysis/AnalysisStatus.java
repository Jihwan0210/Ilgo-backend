package com.ilgo.analysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 분석 상태. 동기 흐름이라 실제로는 DONE/FAILED 만 사용하고,
 * PROCESSING 은 향후 async 대비로 enum 에만 유지한다.
 */
public enum AnalysisStatus {
    PROCESSING("processing"),
    DONE("done"),
    FAILED("failed");

    private final String json;

    AnalysisStatus(String json) {
        this.json = json;
    }

    @JsonValue
    public String getJson() {
        return json;
    }

    @JsonCreator
    public static AnalysisStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toLowerCase();
        for (AnalysisStatus status : values()) {
            if (status.json.equals(v) || status.name().equalsIgnoreCase(v)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}
