package com.ilgo.analysis;

import com.ilgo.common.ApiException;
import com.ilgo.common.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * 보관함 목록 커서: (created_at, id) 를 base64 로 인코딩한 opaque 문자열.
 */
final class CursorCodec {

    private static final String SEP = "|";

    private CursorCodec() {
    }

    static String encode(OffsetDateTime createdAt, UUID id) {
        String raw = createdAt.toString() + SEP + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf(SEP);
            OffsetDateTime createdAt = OffsetDateTime.parse(raw.substring(0, sep));
            UUID id = UUID.fromString(raw.substring(sep + SEP.length()));
            return new Cursor(createdAt, id);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "cursor 값이 올바르지 않습니다.");
        }
    }

    record Cursor(OffsetDateTime createdAt, UUID id) {
    }
}
