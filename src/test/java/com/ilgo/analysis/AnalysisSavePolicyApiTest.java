package com.ilgo.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilgo.ai.AiGatewayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 저장 정책 통합 테스트: 실모드(mock-mode=false)에서 AiGatewayClient 를 stub 해
 * DONE+savable/save 만 저장되고 나머지는 200 응답 + 미저장임을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ilgo_savepolicy;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mock-mode=false",
        "jwt.secret=test-secret-key-for-junit-0123456789-abcdef"
})
class AnalysisSavePolicyApiTest {

    private static final String TINY_IMG = "aGVsbG8=";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private AiGatewayClient aiClient;

    @BeforeEach
    void stubModel() {
        given(aiClient.getModel()).willReturn("test-model");
    }

    private String bearer() throws Exception {
        String email = "u" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"pass1234\"}";
        String resp = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(resp).get("access_token").asText();
    }

    private void stubAi(String content) {
        given(aiClient.complete(any(), any(), any(), any())).willReturn(content);
    }

    private String analyzeBody(boolean save) {
        return """
                {"image_base64":"%s","media_type":"image/png","output_mode":"easy_korean","lang":"ko","save":%s}
                """.formatted(TINY_IMG, save);
    }

    private static final String NOTICE_JSON = """
            {"doc_type":"고지서","summary_one_line":"건강보험료 고지서예요","original_text":"납부 안내","risks":[],"actions":[]}
            """;
    private static final String OTHER_JSON = """
            {"doc_type":"기타","summary_one_line":"무슨 문서인지 모르겠어요","original_text":"","risks":[],"actions":[]}
            """;

    @Test
    void savableDocument_isSaved_and201() throws Exception {
        stubAi(NOTICE_JSON);
        String token = bearer();

        mockMvc.perform(post("/v1/analyses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(analyzeBody(true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("done")))
                .andExpect(jsonPath("$.doc_class", is("notice")))
                .andExpect(jsonPath("$.id", notNullValue()));

        mockMvc.perform(get("/v1/analyses").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(1)));
    }

    @Test
    void notADocument_isUnknown_notSaved_and200() throws Exception {
        stubAi(OTHER_JSON);
        String token = bearer();

        mockMvc.perform(post("/v1/analyses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(analyzeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("done")))
                .andExpect(jsonPath("$.doc_class", is("unknown")))
                .andExpect(jsonPath("$.id", nullValue()));

        mockMvc.perform(get("/v1/analyses").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(0)));
    }

    @Test
    void parseFailure_returnsFailed200_notSaved() throws Exception {
        stubAi("죄송해요, 이미지를 읽을 수 없습니다."); // 비-JSON → 파싱 실패
        String token = bearer();

        mockMvc.perform(post("/v1/analyses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(analyzeBody(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("failed")))
                .andExpect(jsonPath("$.id", nullValue()));

        mockMvc.perform(get("/v1/analyses").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(0)));
    }

    @Test
    void saveFalse_notSaved_and200() throws Exception {
        stubAi(NOTICE_JSON);
        String token = bearer();

        mockMvc.perform(post("/v1/analyses").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(analyzeBody(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", nullValue()));

        mockMvc.perform(get("/v1/analyses").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(0)));
    }
}
