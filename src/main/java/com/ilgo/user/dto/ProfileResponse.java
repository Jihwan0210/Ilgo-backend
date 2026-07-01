package com.ilgo.user.dto;

import com.ilgo.user.OutputMode;

public record ProfileResponse(
        OutputMode outputMode,
        String lang,
        double textScale,
        boolean deadlineAlarm
) {
}
