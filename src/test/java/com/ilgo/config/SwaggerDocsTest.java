package com.ilgo.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * springdoc OpenAPI 문서 생성 및 X-Device-Id 헤더 문서화 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ilgo_swagger;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mock-mode=true"
})
class SwaggerDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_generated_withInfoAndPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title", is("읽고 (Ilgo) API")))
                .andExpect(jsonPath("$.paths./v1/analyses").exists())
                .andExpect(jsonPath("$.paths./v1/analyses/{id}").exists())
                .andExpect(jsonPath("$.paths./v1/profile").exists())
                .andExpect(jsonPath("$.paths./health").exists());
    }

    @Test
    void apiDocs_documentsDeviceIdHeader_onV1_notOnHealth() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // /v1 오퍼레이션에는 X-Device-Id 헤더가 문서화되어야 한다.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$['paths']['/v1/profile']['get']['parameters'][0]['name']",
                        is("X-Device-Id")));

        // 전역적으로 헤더가 존재
        org.assertj.core.api.Assertions.assertThat(body).contains("X-Device-Id");
    }

    @Test
    void schema_fieldNames_areSnakeCase_matchingResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 응답 전체 스키마: snake_case 로 노출, camelCase 는 없어야 함
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['summary_one_line']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['output_mode']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['doc_type']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['reply_drafts']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['created_at']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['summaryOneLine']").doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['replyDrafts']").doesNotExist())
                // 목록 스키마
                .andExpect(jsonPath("$['components']['schemas']['AnalysisListItem']['properties']['top_risk_level']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisListItem']['properties']['card_deadline']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisListResponse']['properties']['next_cursor']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisListItem']['properties']['topRiskLevel']").doesNotExist());
    }

    @Test
    void swaggerUi_isAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/v3/api-docs")));
    }
}
