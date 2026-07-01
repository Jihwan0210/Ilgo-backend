package com.ilgo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilgo.user.User;
import com.ilgo.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ilgo_refresh;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.mock-mode=true",
        "jwt.secret=test-secret-key-for-junit-0123456789-abcdef",
        "jwt.access-expires-in=1800",
        "jwt.refresh-expires-in=1209600"
})
class RefreshTokenApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;

    /** register 후 전체 응답 JSON 문자열 반환. */
    private String register(String email) throws Exception {
        return mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"pass1234\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    private String field(String json, String name) throws Exception {
        return objectMapper.readTree(json).get(name).asText();
    }

    private String refresh(String refreshToken) throws Exception {
        return "{\"refresh_token\":\"" + refreshToken + "\"}";
    }

    private String uniqueEmail(String p) {
        return p + UUID.randomUUID() + "@example.com";
    }

    @Test
    void register_response_includesRefreshTokenAndExpiresIn() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail("r") + "\",\"password\":\"pass1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andExpect(jsonPath("$.token_type", is("Bearer")))
                .andExpect(jsonPath("$.expires_in", is(1800)));
    }

    @Test
    void login_response_includesRefreshToken() throws Exception {
        String email = uniqueEmail("l");
        register(email);
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token", notNullValue()));
    }

    @Test
    void refresh_rotates_issuesNewTokens_andOldRefreshBecomesInvalid() throws Exception {
        String reg = register(uniqueEmail("rot"));
        String r1 = field(reg, "refresh_token");

        String refreshed = mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", not(is(r1))))
                .andExpect(jsonPath("$.token_type", is("Bearer")))
                .andReturn().getResponse().getContentAsString();

        String r2 = field(refreshed, "refresh_token");

        // 새 refresh(r2)는 유효
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r2)))
                .andExpect(status().isOk());

        // 회전된 옛 refresh(r1) 재사용 → 401
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void refresh_reuseOfRevokedToken_revokesAllUserTokens() throws Exception {
        String reg = register(uniqueEmail("reuse"));
        String r1 = field(reg, "refresh_token");

        // 정상 회전 → r2 발급, r1 revoke
        String refreshed = mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String r2 = field(refreshed, "refresh_token");

        // 이미 revoke된 r1 재사용(탈취 정황) → 401 + 유저 전체 revoke
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r1)))
                .andExpect(status().isUnauthorized());

        // 재사용 감지로 r2 도 무효화됨 → 401
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r2)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void refresh_unknownToken_returns401() throws Exception {
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refresh("nonexistent-" + UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void logout_revokesRefreshToken_thenRefreshReturns401() throws Exception {
        String reg = register(uniqueEmail("logout"));
        String r1 = field(reg, "refresh_token");

        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r1)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void logout_isIdempotent() throws Exception {
        String reg = register(uniqueEmail("idem"));
        String r1 = field(reg, "refresh_token");

        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r1)))
                .andExpect(status().isNoContent());
        // 중복 로그아웃 → 여전히 204
        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(r1)))
                .andExpect(status().isNoContent());
        // 미존재 토큰 로그아웃 → 204
        mockMvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refresh("nope-" + UUID.randomUUID())))
                .andExpect(status().isNoContent());
    }

    @Test
    void rawRefreshToken_isNotStored_onlyHashIs() throws Exception {
        String reg = register(uniqueEmail("hash"));
        String raw = field(reg, "refresh_token");

        Integer rawCount = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, raw);
        assertEquals(0, rawCount, "raw refresh token must not be stored");

        Integer hashCount = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, sha256Hex(raw));
        assertEquals(1, hashCount, "sha256 hash of refresh token must be stored");
    }

    @Test
    void reuseOfExpiredRevokedToken_doesNotRevokeActiveTokens() throws Exception {
        // 리뷰 반영: 만료 판정을 재사용 감지보다 먼저 → 만료된(폐기된) 토큰 재제출이
        // 유저의 활성 refresh 전체를 폐기(DoS)하지 못하게 한다.
        String email = uniqueEmail("expreuse");
        String rActive = field(register(email), "refresh_token");

        User user = userRepository.findByEmail(email).orElseThrow();
        String expiredRaw = "expired-" + UUID.randomUUID();
        RefreshToken expired = new RefreshToken(user, sha256Hex(expiredRaw),
                OffsetDateTime.now().minusDays(1));   // 이미 만료
        expired.setRevokedAt(OffsetDateTime.now().minusHours(1)); // 그리고 폐기됨
        refreshTokenRepository.save(expired);

        // 만료+폐기 토큰 제출 → 401 (재사용 감지 트리거 X)
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(expiredRaw)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_REFRESH_TOKEN")));

        // 활성 토큰은 살아있어야 함 (전체 폐기되지 않았음)
        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refresh(rActive)))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentRotateOfSameToken_onlyOneSucceeds() throws Exception {
        // 리뷰 반영: 동일 refresh 로 동시 rotate 시 비관적 락으로 직렬화 → 정확히 1건만 성공.
        String r1 = field(register(uniqueEmail("conc")), "refresh_token");

        int n = 2;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier barrier = new CyclicBarrier(n);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                barrier.await();
                return mockMvc.perform(post("/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON).content(refresh(r1)))
                        .andReturn().getResponse().getStatus();
            }));
        }
        int ok = 0, unauthorized = 0;
        for (Future<Integer> f : futures) {
            int s = f.get();
            if (s == 200) ok++;
            else if (s == 401) unauthorized++;
        }
        pool.shutdown();
        assertEquals(1, ok, "동시 rotate 중 정확히 1건만 성공해야 함");
        assertEquals(1, unauthorized, "나머지 1건은 재사용으로 거부돼야 함");
    }

    private static String sha256Hex(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
