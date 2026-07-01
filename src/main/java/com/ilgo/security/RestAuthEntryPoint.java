package com.ilgo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilgo.common.ErrorCode;
import com.ilgo.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 인증 실패(토큰 없음/만료/위조) → 401 을 공통 에러 포맷으로 반환. */
@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode code = ErrorCode.UNAUTHORIZED;
        response.setStatus(code.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(code.name(), code.getDefaultMessage(), null));
    }
}
