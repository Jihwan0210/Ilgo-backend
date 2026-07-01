package com.ilgo.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 공통 에러 응답 포맷:
 * <pre>{ "error": { "code": "...", "message": "...", "details": {...} } }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, Map<String, Object> details) {
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(new Body(code, message, details));
    }
}
