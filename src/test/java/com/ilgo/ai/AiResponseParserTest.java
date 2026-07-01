package com.ilgo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResponseParserTest {

    // 서비스에서 쓰는 ObjectMapper 와 동일하게 snake_case 전략 사용
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final AiResponseParser parser = new AiResponseParser(objectMapper);

    @Test
    void parsesFullJson() {
        String content = """
                {"doc_type":"고지서","summary_one_line":"요약",
                 "cards":{"what":"납부","when":null,"where":null,"amount":"68,000원","deadline":"2026-08-15"},
                 "risks":[{"level":"yellow","type":"기한","message":"연체"}],
                 "actions":[{"text":"내세요","priority":1}],
                 "reply_drafts":[]}
                """;

        AiAnalysis result = parser.parse(content);

        assertThat(result.docType()).isEqualTo("고지서");
        assertThat(result.cards().amount()).isEqualTo("68,000원");
        assertThat(result.cards().deadline()).isEqualTo("2026-08-15");
        assertThat(result.risks()).hasSize(1);
        assertThat(result.risks().get(0).level()).isEqualTo("yellow");
        assertThat(result.actions().get(0).priority()).isEqualTo(1);
        assertThat(result.replyDrafts()).isEmpty();
    }

    @Test
    void parsesJsonWithCodeFence() {
        String content = "```json\n{\"doc_type\":\"메시지\",\"reply_drafts\":[{\"korean\":\"네\",\"note_in_lang\":\"yes\"}]}\n```";

        AiAnalysis result = parser.parse(content);

        assertThat(result.docType()).isEqualTo("메시지");
        assertThat(result.replyDrafts()).hasSize(1);
        assertThat(result.replyDrafts().get(0).noteInLang()).isEqualTo("yes");
    }

    @Test
    void extractsJsonEvenWithSurroundingText() {
        String content = "여기 결과입니다: {\"doc_type\":\"공지\",\"reply_drafts\":[]} 이상입니다.";

        AiAnalysis result = parser.parse(content);

        assertThat(result.docType()).isEqualTo("공지");
    }

    @Test
    void throwsOnGarbage() {
        assertThatThrownBy(() -> parser.parse("완전히 깨진 응답 no json here"))
                .isInstanceOf(AiParseException.class);
    }
}
