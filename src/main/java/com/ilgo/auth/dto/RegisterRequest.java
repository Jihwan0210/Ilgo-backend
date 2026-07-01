package com.ilgo.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(
        @Schema(example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(example = "pass1234", requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}
