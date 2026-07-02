package com.ilgo.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /v1/analyses 통합 테스트 (MOCK_MODE=true, JWT 인증).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ilgo_api;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mock-mode=true",
        "app.mock-save=false",
        "jwt.secret=test-secret-key-for-junit-0123456789-abcdef"
})
class AnalysisApiMockModeTest {

    private static final String TINY_IMG = "aGVsbG8="; // mock 모드라 실제 내용 미사용

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    /** 새 계정을 가입하고 "Bearer <token>" 을 반환. */
    private String bearer() throws Exception {
        String email = "u" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"pass1234\"}";
        String resp = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(resp).get("access_token").asText();
    }

    @Test
    void easyKorean_returnsHealthMock_withEmptyReplyDrafts() throws Exception {
        String body = """
                {"image_base64":"%s","media_type":"image/png","output_mode":"easy_korean","lang":"ko"}
                """.formatted(TINY_IMG);

        mockMvc.perform(post("/v1/analyses")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("done")))
                .andExpect(jsonPath("$.output_mode", is("easy_korean")))
                .andExpect(jsonPath("$.doc_type", is("고지서")))
                .andExpect(jsonPath("$.cards.amount", is("68,000원")))
                .andExpect(jsonPath("$.cards.deadline", is("2026-08-15")))
                .andExpect(jsonPath("$.cards.when", nullValue()))
                .andExpect(jsonPath("$.risks", hasSize(1)))
                .andExpect(jsonPath("$.risks[0].level", is("yellow")))
                .andExpect(jsonPath("$.actions", hasSize(2)))
                .andExpect(jsonPath("$.actions[0].is_done", is(false)))
                .andExpect(jsonPath("$.reply_drafts", hasSize(0)))
                .andExpect(jsonPath("$.created_at", notNullValue()));
    }

    @Test
    void native_returnsWageMock_withTwoReplyDrafts() throws Exception {
        String body = """
                {"image_base64":"%s","media_type":"image/jpeg","output_mode":"native","lang":"en"}
                """.formatted(TINY_IMG);

        mockMvc.perform(post("/v1/analyses")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.output_mode", is("native")))
                .andExpect(jsonPath("$.risks[0].level", is("red")))
                .andExpect(jsonPath("$.reply_drafts", hasSize(2)))
                .andExpect(jsonPath("$.reply_drafts[0].note_in_lang", notNullValue()));
    }

    @Test
    void easyKorean_withFineHint_returnsFineMock() throws Exception {
        String body = """
                {"image_base64":"%s","media_type":"image/png","output_mode":"easy_korean","lang":"ko","hint":"주차 과태료"}
                """.formatted(TINY_IMG);

        mockMvc.perform(post("/v1/analyses")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.risks[0].level", is("red")))
                .andExpect(jsonPath("$.cards.amount", is("40,000원")));
    }

    @Test
    void easyKorean_withSmishingHint_returnsSmishingRisk() throws Exception {
        String body = """
                {"image_base64":"%s","media_type":"image/png","output_mode":"easy_korean","lang":"ko","hint":"스미싱 의심 문자"}
                """.formatted(TINY_IMG);

        mockMvc.perform(post("/v1/analyses")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doc_type", is("메시지")))
                .andExpect(jsonPath("$.risks[0].level", is("red")))
                .andExpect(jsonPath("$.risks[0].type", anyOf(is("사기"), is("스미싱"))))
                .andExpect(jsonPath("$.actions[*].text", hasItem(containsString("링크"))))
                .andExpect(jsonPath("$.actions[*].text", hasItem(containsString("118"))))
                .andExpect(jsonPath("$.reply_drafts", hasSize(0)));
    }

    @Test
    void list_inMockMode_returnsListJsonInFileOrder() throws Exception {
        mockMvc.perform(get("/v1/analyses").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].id", is("anl_0001")))
                .andExpect(jsonPath("$.items[0].top_risk_level", is("yellow")))
                .andExpect(jsonPath("$.items[2].card_deadline", nullValue()))
                .andExpect(jsonPath("$.next_cursor", nullValue()));
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/analyses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    void unsupportedMediaType_returns400() throws Exception {
        String body = """
                {"image_base64":"%s","media_type":"image/gif","output_mode":"easy_korean","lang":"ko"}
                """.formatted(TINY_IMG);

        mockMvc.perform(post("/v1/analyses")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("UNSUPPORTED_MEDIA_TYPE")));
    }

    @Test
    void missingRequiredField_returns400InvalidRequest() throws Exception {
        String body = """
                {"media_type":"image/png","output_mode":"easy_korean","lang":"ko"}
                """;

        mockMvc.perform(post("/v1/analyses")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_REQUEST")));
    }
}
