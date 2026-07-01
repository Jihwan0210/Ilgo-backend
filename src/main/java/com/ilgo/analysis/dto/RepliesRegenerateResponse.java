package com.ilgo.analysis.dto;

import java.util.List;

public record RepliesRegenerateResponse(
        List<ReplyDraftDto> replyDrafts
) {
}
