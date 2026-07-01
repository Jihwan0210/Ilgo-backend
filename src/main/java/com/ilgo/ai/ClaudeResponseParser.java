package com.ilgo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Claude 응답 텍스트를 {@link ClaudeAnalysis} 로 파싱한다.
 * 요청 시 assistant 를 "{" 로 prefill 하므로, 반환 텍스트 앞에 "{" 를 보정한다.
 */
@Component
public class ClaudeResponseParser {

    private final ObjectMapper objectMapper;

    public ClaudeResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ClaudeAnalysis parse(String assistantText) {
        if (assistantText == null) {
            throw new ClaudeParseException("empty AI response");
        }
        String text = assistantText.trim();

        // 코드펜스 제거
        if (text.startsWith("```")) {
            text = text.replaceAll("^```(?:json)?", "").replaceAll("```$", "").trim();
        }
        // prefill "{" 보정: 완전한 JSON 이 아니면 앞에 "{" 를 붙인다.
        if (!text.startsWith("{")) {
            text = "{" + text;
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            throw new ClaudeParseException("no JSON object found in AI response");
        }
        String json = text.substring(start, end + 1);

        try {
            ClaudeAnalysis parsed = objectMapper.readValue(json, ClaudeAnalysis.class);
            if (parsed == null) {
                throw new ClaudeParseException("parsed to null");
            }
            return parsed;
        } catch (ClaudeParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ClaudeParseException("failed to parse AI JSON", e);
        }
    }
}
