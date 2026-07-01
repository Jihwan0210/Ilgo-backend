package com.ilgo.common;

import com.ilgo.user.User;
import com.ilgo.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * 모든 /v1 요청에서 X-Device-Id(uuid) 헤더를 검증하고 User 를 upsert 하여
 * 요청 속성({@link #USER_ATTRIBUTE})에 저장한다.
 */
@Component
public class DeviceIdInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Device-Id";
    public static final String USER_ATTRIBUTE = "ilgo.currentUser";

    private final UserService userService;

    public DeviceIdInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String deviceId = request.getHeader(HEADER);
        if (!StringUtils.hasText(deviceId)) {
            throw new ApiException(ErrorCode.MISSING_DEVICE_ID);
        }
        UUID parsed;
        try {
            parsed = UUID.fromString(deviceId.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "X-Device-Id 는 UUID 형식이어야 합니다.",
                    Map.of("header", HEADER));
        }
        User user = userService.getOrCreate(parsed.toString());
        request.setAttribute(USER_ATTRIBUTE, user);
        return true;
    }
}
