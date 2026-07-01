package com.ilgo.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ilgo_profile;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mock-mode=true",
        "jwt.secret=test-secret-key-for-junit-0123456789-abcdef"
})
class ProfileApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token() throws Exception {
        String email = "prof" + UUID.randomUUID() + "@example.com";
        String resp = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"pass1234\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(resp).get("access_token").asText();
    }

    @Test
    void get_returnsAllFourFields_withDefaults() throws Exception {
        mockMvc.perform(get("/v1/profile").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output_mode", is("easy_korean")))
                .andExpect(jsonPath("$.lang", is("ko")))
                .andExpect(jsonPath("$.text_scale", is(1.0)))
                .andExpect(jsonPath("$.deadline_alarm", is(true)));
    }

    @Test
    void put_fullUpdate_persistsAllFields() throws Exception {
        String auth = token();
        mockMvc.perform(put("/v1/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"output_mode\":\"native\",\"lang\":\"km\",\"text_scale\":1.15,\"deadline_alarm\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output_mode", is("native")))
                .andExpect(jsonPath("$.lang", is("km")))
                .andExpect(jsonPath("$.text_scale", is(1.15)))
                .andExpect(jsonPath("$.deadline_alarm", is(false)));
    }

    @Test
    void put_partialUpdate_keepsUnspecifiedFields() throws Exception {
        String auth = token();
        // 먼저 native/km 로 저장
        mockMvc.perform(put("/v1/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"output_mode\":\"native\",\"lang\":\"km\",\"text_scale\":1.3,\"deadline_alarm\":false}"))
                .andExpect(status().isOk());

        // text_scale 만 부분 업데이트 → 나머지는 유지
        mockMvc.perform(put("/v1/profile").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text_scale\":0.9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text_scale", is(0.9)))
                .andExpect(jsonPath("$.output_mode", is("native")))
                .andExpect(jsonPath("$.lang", is("km")))
                .andExpect(jsonPath("$.deadline_alarm", is(false)));
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/profile"))
                .andExpect(status().isUnauthorized());
    }
}
