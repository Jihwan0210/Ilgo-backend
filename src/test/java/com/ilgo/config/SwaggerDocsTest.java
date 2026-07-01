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
 * springdoc OpenAPI 문서 생성 + JWT(bearerAuth) 연동 + snake_case 스키마 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ilgo_swagger;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mock-mode=true",
        "jwt.secret=test-secret-key-for-junit-0123456789-abcdef"
})
class SwaggerDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_generated_withInfoAndPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title", is("읽고 (Ilgo) API")))
                .andExpect(jsonPath("$['paths']['/v1/analyses']").exists())
                .andExpect(jsonPath("$['paths']['/v1/auth/register']").exists())
                .andExpect(jsonPath("$['paths']['/v1/auth/login']").exists())
                .andExpect(jsonPath("$['paths']['/health']").exists());
    }

    @Test
    void apiDocs_declaresBearerAuthScheme_andGlobalRequirement() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['components']['securitySchemes']['bearerAuth']['type']", is("http")))
                .andExpect(jsonPath("$['components']['securitySchemes']['bearerAuth']['scheme']", is("bearer")))
                .andExpect(jsonPath("$['components']['securitySchemes']['bearerAuth']['bearerFormat']", is("JWT")))
                // 전역 보안 요구 존재
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());
    }

    @Test
    void schema_fieldNames_areSnakeCase_matchingResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['summary_one_line']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['reply_drafts']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisResponse']['properties']['summaryOneLine']").doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisListItem']['properties']['top_risk_level']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AnalysisListResponse']['properties']['next_cursor']").exists())
                // 인증 응답도 snake_case
                .andExpect(jsonPath("$['components']['schemas']['AuthResponse']['properties']['access_token']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AuthResponse']['properties']['token_type']").exists())
                .andExpect(jsonPath("$['components']['schemas']['AuthResponse']['properties']['accessToken']").doesNotExist());
    }

    @Test
    void swaggerUi_isAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/v3/api-docs")));
    }
}
