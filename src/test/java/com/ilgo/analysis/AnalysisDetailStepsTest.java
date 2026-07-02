package com.ilgo.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilgo.user.OutputMode;
import com.ilgo.user.User;
import com.ilgo.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회귀: 저장된 분석을 단건 조회(GET /v1/analyses/{id})할 때 risks[].steps(@ElementCollection, LAZY)를
 * 세션 종료 후 직렬화하다 LazyInitializationException → 500 나던 버그.
 * (open-in-view=false 라 컨트롤러 반환 후 직렬화 시점엔 세션이 없다.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ilgo_detail;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "app.mock-mode=true",
        "jwt.secret=test-secret-key-for-junit-0123456789-abcdef"
})
class AnalysisDetailStepsTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AnalysisRepository analysisRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void getDetail_withRiskDetailAndSteps_returns200() throws Exception {
        String email = "detail" + UUID.randomUUID() + "@example.com";
        String resp = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"pass1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(resp).get("access_token").asText();
        UUID userId = UUID.fromString(objectMapper.readTree(resp).get("user").get("id").asText());

        User user = userRepository.findById(userId).orElseThrow();
        Analysis a = new Analysis();
        a.setUser(user);
        a.setStatus(AnalysisStatus.DONE);
        a.setOutputMode(OutputMode.NATIVE);
        a.setLang("km");
        a.setDocType("메시지");
        a.setSaved(true);
        a.addRisk(new Risk(RiskLevel.RED, "임금", "부당 공제일 수 있어요",
                "근로계약서에 정한 임금은 지켜야 해요.", List.of("계약서 확인", "서면 요청", "상담 전화")));
        Analysis saved = analysisRepository.save(a);

        mockMvc.perform(get("/v1/analyses/{id}", saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.risks[0].detail", is("근로계약서에 정한 임금은 지켜야 해요.")))
                .andExpect(jsonPath("$.risks[0].steps.length()", is(3)))
                .andExpect(jsonPath("$.risks[0].steps[0]", is("계약서 확인")));
    }

    @Test
    void getDetail_withRiskWithoutSteps_returns200() throws Exception {
        String email = "nosteps" + UUID.randomUUID() + "@example.com";
        String resp = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"pass1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(resp).get("access_token").asText();
        UUID userId = UUID.fromString(objectMapper.readTree(resp).get("user").get("id").asText());

        User user = userRepository.findById(userId).orElseThrow();
        Analysis a = new Analysis();
        a.setUser(user);
        a.setStatus(AnalysisStatus.DONE);
        a.setOutputMode(OutputMode.EASY_KOREAN);
        a.setLang("ko");
        a.setDocType("고지서");
        a.setSaved(true);
        a.addRisk(new Risk(RiskLevel.GREEN, "기타", "안전해요")); // steps 없음
        Analysis saved = analysisRepository.save(a);

        mockMvc.perform(get("/v1/analyses/{id}", saved.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.risks[0].steps.length()", is(0)));
    }
}
