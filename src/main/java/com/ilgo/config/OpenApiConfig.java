package com.ilgo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilgo.common.CurrentUser;
import com.ilgo.common.HealthController;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String DEVICE_HEADER = "X-Device-Id";

    static {
        // @CurrentUser 로 주입되는 User 파라미터는 문서에서 제외 (커스텀 ArgumentResolver 로 처리되므로)
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    /**
     * OpenAPI 스키마 생성기가 Spring 의 Jackson ObjectMapper(SNAKE_CASE) 를 따르도록 한다.
     * 이렇게 해야 문서의 필드명(Example Value/Schema)이 실제 응답과 동일한 snake_case 로 나온다.
     * (기본 ModelResolver 는 자체 매퍼를 써서 camelCase 로 표기됨)
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

                        **인증:** 모든 `/v1` 요청은 헤더 `X-Device-Id`(UUID) 필수.
                        **에러 포맷:** `{ "error": { "code", "message", "details" } }`
                        """)
                .contact(new Contact().name("Ilgo Backend").email("team5@aidedu.org"))
                .license(new License().name("Hackathon"));

        return new OpenAPI()
                .info(info)
                .servers(List.of(local, prod));
    }

    /**
     * /v1 엔드포인트 전체에 X-Device-Id 헤더 파라미터를 문서화한다. (/health 제외)
     */
    @Bean
    public OperationCustomizer deviceIdHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            if (HealthController.class.equals(handlerMethod.getBeanType())) {
                return operation;
            }
            boolean already = operation.getParameters() != null
                    && operation.getParameters().stream()
                    .anyMatch(p -> DEVICE_HEADER.equals(p.getName()));
            if (!already) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name(DEVICE_HEADER)
                        .required(true)
                        .schema(new StringSchema().format("uuid"))
                        .description("익명 기기 식별자(UUID). 최초 실행 시 앱이 생성·저장."));
            }
            return operation;
        };
    }
}
