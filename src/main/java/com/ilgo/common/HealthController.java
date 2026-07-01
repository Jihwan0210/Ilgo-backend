package com.ilgo.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Tag(name = "Health", description = "헬스체크")
@SecurityRequirements // 인증 불필요 → Swagger 자물쇠 제외
public class HealthController {

    @Operation(summary = "헬스체크", description = "서버 상태와 현재 UTC 시각을 반환한다.")
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("time", OffsetDateTime.now(ZoneOffset.UTC));
        return body;
    }
}
