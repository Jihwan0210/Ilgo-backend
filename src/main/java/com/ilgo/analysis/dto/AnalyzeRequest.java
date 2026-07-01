package com.ilgo.analysis.dto;

import com.ilgo.user.OutputMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /v1/analyses 요청 (application/json).
 * multipart/form-data 요청은 컨트롤러에서 이 DTO 로 변환된다.
 */
public record AnalyzeRequest(

        @Schema(description = "이미지 base64", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String imageBase64,

        @Schema(description = "미디어 타입", example = "image/jpeg", allowableValues = {"image/jpeg", "image/png"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String mediaType,

        @Schema(description = "출력 모드. 생략 시 사용자 기본값 사용", example = "easy_korean")
        OutputMode outputMode,

        @Schema(description = "언어(ISO 639-1). 생략 시 사용자 기본값 사용", example = "ko")
        String lang,

        @Schema(description = "보관함 저장 여부(기본 true)", example = "true")
        Boolean save,

        @Schema(description = "MOCK_MODE 전용 힌트(easy 모드에서 health/fine 선택). 실모드에서는 무시됨",
                example = "과태료")
        String hint
) {
}
