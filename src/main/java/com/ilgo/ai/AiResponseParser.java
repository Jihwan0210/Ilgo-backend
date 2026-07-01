package com.ilgo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * AI 응답 텍스트(choices[0].message.content)를 {@link AiAnalysis} 로 파싱한다.
 * 코드펜스 제거 + 첫 '{' ~ 마지막 '}' 구간을 추출해 파싱한다.
 */
@Component
public class AiResponseParser {

    private final ObjectMapper objectMapper;

    public AiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiAnalysis parse(String content) {
        if (content == null) {
            throw new AiParseException("empty AI response");
        }
        String text = content.trim();

        // 코드펜스 제거
        if (text.startsWith("```")) {
            text = text.replaceAll("^```(?:json)?", "").replaceAll("```$", "").trim();
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            throw new AiParseException("no JSON object found in AI response");
        }
        String json = text.substring(start, end + 1);

        try {
            AiAnalysis parsed = objectMapper.readValue(json, AiAnalysis.class);
            if (parsed == null) {
                throw new AiParseException("parsed to null");
            }
            return parsed;
        } catch (AiParseException e) {
            throw e;
        } catch (Exception e) {
            throw new AiParseException("failed to parse AI JSON", e);
        }
    }
}
