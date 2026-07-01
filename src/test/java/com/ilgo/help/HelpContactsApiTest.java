package com.ilgo.help;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ilgo_help;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mock-mode=true",
        "jwt.secret=test-secret-key-for-junit-0123456789-abcdef"
})
class HelpContactsApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token() throws Exception {
        String email = "help" + UUID.randomUUID() + "@example.com";
        String resp = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"pass1234\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(resp).get("access_token").asText();
    }

    @Test
    void list_returnsContacts_withEmergencyItem() throws Exception {
        mockMvc.perform(get("/v1/help-contacts").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", greaterThan(0)))
                .andExpect(jsonPath("$.items[*].phone", hasItem("112")))
                .andExpect(jsonPath("$.items[*].emergency", hasItem(true)))
                .andExpect(jsonPath("$.items[0].id").exists())
                .andExpect(jsonPath("$.items[0].title").exists())
                .andExpect(jsonPath("$.items[0].category").exists());
    }

    @Test
    void list_withLangAndDocTypeQuery_stillReturnsItems() throws Exception {
        mockMvc.perform(get("/v1/help-contacts?lang=km&doc_type=메시지").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", greaterThan(0)));
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/v1/help-contacts"))
                .andExpect(status().isUnauthorized());
    }
}
