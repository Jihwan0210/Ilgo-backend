package com.ilgo.analysis.dto;

import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
        @NotNull Boolean isHelpful,
        String reason
) {
}
