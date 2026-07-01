package com.ilgo.ai;

/** Claude 응답을 JSON 으로 파싱하지 못했을 때. */
public class ClaudeParseException extends RuntimeException {

    public ClaudeParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClaudeParseException(String message) {
        super(message);
    }
}
