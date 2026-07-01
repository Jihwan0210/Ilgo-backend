package com.ilgo.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 출력 모드. DB/엔티티는 enum 이름(EASY_KOREAN/NATIVE)으로 저장하고,
 * API JSON 은 소문자(easy_korean/native)로 직렬화한다.
 */
public enum OutputMode {
    EASY_KOREAN("easy_korean"),
    NATIVE("native");

    private final String json;

    OutputMode(String json) {
        this.json = json;
    }

    @JsonValue
    public String getJson() {
        return json;
    }

    @JsonCreator
    public static OutputMode fromJson(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toLowerCase();
        for (OutputMode mode : values()) {
            if (mode.json.equals(v) || mode.name().equalsIgnoreCase(v)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown output_mode: " + value);
    }
}
