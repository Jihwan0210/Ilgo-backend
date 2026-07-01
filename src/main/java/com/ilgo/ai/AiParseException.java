package com.ilgo.ai;

/** AI 응답을 JSON 으로 파싱하지 못했을 때. */
public class AiParseException extends RuntimeException {

    public AiParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiParseException(String message) {
        super(message);
    }
}
