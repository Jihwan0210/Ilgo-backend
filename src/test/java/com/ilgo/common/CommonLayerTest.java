package com.ilgo.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonLayerTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private MockHttpServletRequest post() {
        return new MockHttpServletRequest("POST", "/v1/analyses");
    }

    // ---- RateLimitInterceptor (인증 userId 기준) ----

    @Test
    void rateLimit_allowsUpToLimitThenBlocks() {
        authenticateAs(UUID.randomUUID());
        RateLimitInterceptor interceptor = new RateLimitInterceptor(2);

        assertThat(interceptor.preHandle(post(), response, new Object())).isTrue();
        assertThat(interceptor.preHandle(post(), response, new Object())).isTrue();

        assertThatThrownBy(() -> interceptor.preHandle(post(), response, new Object()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void rateLimit_separateUsersAreIndependent() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1);

        authenticateAs(UUID.randomUUID());
        assertThat(interceptor.preHandle(post(), response, new Object())).isTrue();

        // 다른 사용자는 별도 카운터
        SecurityContextHolder.clearContext();
        authenticateAs(UUID.randomUUID());
        assertThat(interceptor.preHandle(post(), response, new Object())).isTrue();
    }

    @Test
    void rateLimit_ignoresNonPost() {
        authenticateAs(UUID.randomUUID());
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1);
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/v1/analyses");

        assertThat(interceptor.preHandle(get, response, new Object())).isTrue();
        assertThat(interceptor.preHandle(get, response, new Object())).isTrue();
    }

    // ---- GlobalExceptionHandler ----

    @Test
    void handler_apiException_mapsCodeAndStatus() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ErrorResponse> resp = handler.handleApi(new ApiException(ErrorCode.NOT_FOUND));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().error().code()).isEqualTo("NOT_FOUND");
        assertThat(resp.getBody().error().message()).isEqualTo(ErrorCode.NOT_FOUND.getDefaultMessage());
    }

    @Test
    void handler_notNativeMode_is409() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ErrorResponse> resp = handler.handleApi(new ApiException(ErrorCode.NOT_NATIVE_MODE));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().error().code()).isEqualTo("NOT_NATIVE_MODE");
    }
}
