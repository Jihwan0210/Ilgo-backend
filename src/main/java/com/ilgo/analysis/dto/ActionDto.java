package com.ilgo.analysis.dto;

public record ActionDto(
        String id,
        String text,
        Integer priority,
        boolean isDone
) {
}
