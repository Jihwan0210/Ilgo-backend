package com.ilgo.analysis;

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

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실모드(비-목업) 통합 테스트. 데이터는 리포지토리로 직접 시딩한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ilgo_real;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mock-mode=false"
})
class AnalysisRealModeApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AnalysisRepository analysisRepository;

    private User newUser() {
        return userRepository.save(new User(UUID.randomUUID().toString()));
    }

    private Analysis baseAnalysis(User user, OutputMode mode) {
        Analysis a = new Analysis();
        a.setUser(user);
        a.setStatus(AnalysisStatus.DONE);
        a.setOutputMode(mode);
        a.setLang(mode == OutputMode.NATIVE ? "en" : "ko");
        a.setDocType("고지서");
        a.setSummaryOneLine("요약");
        return a;
    }

    // ---- #4 created_at DESC 정렬 + #3 top_risk_level 계산 ----

    @Test
    void list_ordersByCreatedAtDesc_andComputesTopRiskLevel() throws Exception {
        User user = newUser();

        Analysis a1 = baseAnalysis(user, OutputMode.EASY_KOREAN); // yellow
        a1.addRisk(new Risk(RiskLevel.YELLOW, "기한", "노랑"));
        analysisRepository.saveAndFlush(a1);
        sleep();

        Analysis a2 = baseAnalysis(user, OutputMode.EASY_KOREAN); // green + red → red
        a2.addRisk(new Risk(RiskLevel.GREEN, "기타", "초록"));
        a2.addRisk(new Risk(RiskLevel.RED, "과태료", "빨강"));
        analysisRepository.saveAndFlush(a2);
        sleep();

        Analysis a3 = baseAnalysis(user, OutputMode.EASY_KOREAN); // no risks → null
        analysisRepository.saveAndFlush(a3);

        mockMvc.perform(get("/v1/analyses").header("X-Device-Id", user.getDeviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id", is(a3.getId().toString())))     // 최신순
                .andExpect(jsonPath("$.items[1].id", is(a2.getId().toString())))
                .andExpect(jsonPath("$.items[2].id", is(a1.getId().toString())))
                .andExpect(jsonPath("$.items[0].top_risk_level", nullValue()))       // a3: 위험 없음
                .andExpect(jsonPath("$.items[1].top_risk_level", is("red")))         // a2: red>green
                .andExpect(jsonPath("$.items[2].top_risk_level", is("yellow")))      // a1
                .andExpect(jsonPath("$.next_cursor", nullValue()));
    }

    // ---- 단건 조회: 소유자/404/403 ----

    @Test
    void getOne_owner200_unknown404_other403() throws Exception {
        User owner = newUser();
        Analysis a = analysisRepository.saveAndFlush(baseAnalysis(owner, OutputMode.EASY_KOREAN));

        mockMvc.perform(get("/v1/analyses/{id}", a.getId()).header("X-Device-Id", owner.getDeviceId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(a.getId().toString())));

        mockMvc.perform(get("/v1/analyses/{id}", UUID.randomUUID()).header("X-Device-Id", owner.getDeviceId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("NOT_FOUND")));

        mockMvc.perform(get("/v1/analyses/{id}", a.getId()).header("X-Device-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));

        mockMvc.perform(get("/v1/analyses/not-a-uuid").header("X-Device-Id", owner.getDeviceId()))
                .andExpect(status().isNotFound());
    }

    // ---- 삭제 204 → 이후 404 ----

    @Test
    void delete_removesAnalysis() throws Exception {
        User user = newUser();
        Analysis a = analysisRepository.saveAndFlush(baseAnalysis(user, OutputMode.EASY_KOREAN));

        mockMvc.perform(delete("/v1/analyses/{id}", a.getId()).header("X-Device-Id", user.getDeviceId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/analyses/{id}", a.getId()).header("X-Device-Id", user.getDeviceId()))
                .andExpect(status().isNotFound());
    }

    // ---- 할 일 토글 ----

    @Test
    void toggleAction_updatesIsDone() throws Exception {
        User user = newUser();
        Analysis a = baseAnalysis(user, OutputMode.EASY_KOREAN);
        a.addAction(new Action("할 일", 1));
        analysisRepository.saveAndFlush(a);
        UUID actionId = a.getActions().get(0).getId();

        mockMvc.perform(patch("/v1/analyses/{id}/actions/{actionId}", a.getId(), actionId)
                        .header("X-Device-Id", user.getDeviceId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_done\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(actionId.toString())))
                .andExpect(jsonPath("$.is_done", is(true)));
    }

    // ---- 답장 재생성: easy 모드 → 409 ----

    @Test
    void regenerateReplies_onEasyMode_returns409() throws Exception {
        User user = newUser();
        Analysis a = analysisRepository.saveAndFlush(baseAnalysis(user, OutputMode.EASY_KOREAN));

        mockMvc.perform(post("/v1/analyses/{id}/replies:regenerate", a.getId())
                        .header("X-Device-Id", user.getDeviceId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("NOT_NATIVE_MODE")));
    }

    // ---- 피드백 201 ----

    @Test
    void feedback_created201() throws Exception {
        User user = newUser();
        Analysis a = analysisRepository.saveAndFlush(baseAnalysis(user, OutputMode.EASY_KOREAN));

        mockMvc.perform(post("/v1/analyses/{id}/feedback", a.getId())
                        .header("X-Device-Id", user.getDeviceId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_helpful\":false,\"reason\":\"금액이 틀림\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    // ---- 프로필 GET/PUT ----

    @Test
    void profile_getDefaults_thenUpdate() throws Exception {
        String device = UUID.randomUUID().toString();

        mockMvc.perform(get("/v1/profile").header("X-Device-Id", device))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output_mode", is("easy_korean")))
                .andExpect(jsonPath("$.lang", is("ko")));

        mockMvc.perform(put("/v1/profile").header("X-Device-Id", device)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"output_mode\":\"native\",\"lang\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output_mode", is("native")))
                .andExpect(jsonPath("$.lang", is("en")));

        mockMvc.perform(get("/v1/profile").header("X-Device-Id", device))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output_mode", is("native")))
                .andExpect(jsonPath("$.lang", is("en")));
    }

    private void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
