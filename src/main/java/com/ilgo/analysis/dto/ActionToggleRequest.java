package com.ilgo.analysis.dto;

import jakarta.validation.constraints.NotNull;

public record ActionToggleRequest(
        @NotNull Boolean isDone
) {
}
