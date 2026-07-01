package com.ilgo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilgo.common.CurrentUser;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    static {
        // @CurrentUser 로 주입되는 User 파라미터는 문서에서 제외 (커스텀 ArgumentResolver 로 처리되므로)
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    /**
     * OpenAPI 스키마 생성기가 Spring 의 Jackson ObjectMapper(SNAKE_CASE) 를 따르도록 한다.
     * 문서의 필드명(Example Value/Schema)이 실제 응답과 동일한 snake_case 로 나온다.
     */
    @Bean
    public ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }

    @Bean
    public OpenAPI ilgoOpenAPI() {
        Server local = new Server().url("http://localhost:8080").description("로컬");
        Server prod = new Server().url("https://ilgo-api.up.railway.app").description("Railway (배포)");

        Info info = new Info()
                .title("읽고 (Ilgo) API")
                .version("v1")
                .description("""
                        어려운 한국어 글 이미지를 받아 쉬운말/모국어 풀이·위험 신호등·할 일·(외국인용)답장을
                        만들어 저장/반환하는 API.

                        **인증:** 이메일/비밀번호 로그인 후 발급되는 JWT 를 `Authorization: Bearer <token>` 로 전달.
                        `POST /v1/auth/register`, `POST /v1/auth/login`, `GET /health` 는 인증 불필요.
                        **에러 포맷:** `{ "error": { "code", "message", "details" } }`
                        """)
                .contact(new Contact().name("Ilgo Backend").email("team5@aidedu.org"))
                .license(new License().name("Hackathon"));

        SecurityScheme bearerScheme = new SecurityScheme()
                .name(BEARER)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(info)
                .servers(List.of(local, prod))
                .components(new Components().addSecuritySchemes(BEARER, bearerScheme))
                // 전역 보안 요구(자물쇠). auth/health 는 @SecurityRequirements 로 개별 제외.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
