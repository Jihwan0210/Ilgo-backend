package com.ilgo.analysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 위험 신호등. rank 로 최고 위험 레벨(top_risk_level) 계산에 사용한다. (RED > YELLOW > GREEN)
 */
public enum RiskLevel {
    GREEN("green", 0),
    YELLOW("yellow", 1),
    RED("red", 2);

    private final String json;
    private final int rank;

    RiskLevel(String json, int rank) {
        this.json = json;
        this.rank = rank;
    }

    @JsonValue
    public String getJson() {
        return json;
    }

    public int getRank() {
        return rank;
    }

    @JsonCreator
    public static RiskLevel fromJson(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toLowerCase();
        for (RiskLevel level : values()) {
            if (level.json.equals(v) || level.name().equalsIgnoreCase(v)) {
                return level;
            }
        }
        // 확신 없으면 yellow (스펙: 확신 없으면 yellow)
        return YELLOW;
    }
}
