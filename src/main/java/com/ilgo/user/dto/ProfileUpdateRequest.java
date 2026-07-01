package com.ilgo.user.dto;

import com.ilgo.user.OutputMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProfileUpdateRequest(
        @NotNull OutputMode outputMode,
        @NotBlank String lang
) {
}
