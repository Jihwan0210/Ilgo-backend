package com.ilgo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeResponseParserTest {

    // 서비스에서 쓰는 ObjectMapper 와 동일하게 snake_case 전략 사용
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final ClaudeResponseParser parser = new ClaudeResponseParser(objectMapper);

    @Test
    void parsesPrefilledJson_prependsBrace() {
        // assistant 가 "{" 로 prefill 되었으므로 반환 텍스트는 여는 중괄호가 없다.
        String text = """
                "doc_type":"고지서","summary_one_line":"요약",
                "cards":{"what":"납부","when":null,"where":null,"amount":"68,000원","deadline":"2026-08-15"},
                "risks":[{"level":"yellow","type":"기한","message":"연체"}],
                "actions":[{"text":"내세요","priority":1}],
                "reply_drafts":[]}
                """;

        ClaudeAnalysis result = parser.parse(text);

        assertThat(result.docType()).isEqualTo("고지서");
        assertThat(result.cards().amount()).isEqualTo("68,000원");
        assertThat(result.cards().deadline()).isEqualTo("2026-08-15");
        assertThat(result.risks()).hasSize(1);
        assertThat(result.risks().get(0).level()).isEqualTo("yellow");
        assertThat(result.actions().get(0).priority()).isEqualTo(1);
        assertThat(result.replyDrafts()).isEmpty();
    }

    @Test
    void parsesFullJson_withCodeFence() {
        String text = "```json\n{\"doc_type\":\"메시지\",\"reply_drafts\":[{\"korean\":\"네\",\"note_in_lang\":\"yes\"}]}\n```";

        ClaudeAnalysis result = parser.parse(text);

        assertThat(result.docType()).isEqualTo("메시지");
        assertThat(result.replyDrafts()).hasSize(1);
        assertThat(result.replyDrafts().get(0).noteInLang()).isEqualTo("yes");
    }

    @Test
    void throwsOnGarbage() {
        assertThatThrownBy(() -> parser.parse("완전히 깨진 응답 no json here"))
                .isInstanceOf(ClaudeParseException.class);
    }
}
