package com.ilgo.common;

import com.ilgo.user.User;
import com.ilgo.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommonLayerTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    // ---- DeviceIdInterceptor ----

    @Test
    void deviceInterceptor_missingHeader_throwsMissingDeviceId() {
        DeviceIdInterceptor interceptor = new DeviceIdInterceptor(mock(UserService.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/analyses");

        assertThatThrownBy(() -> interceptor.preHandle(req, response, new Object()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.MISSING_DEVICE_ID);
    }

    @Test
    void deviceInterceptor_invalidUuid_throwsInvalidRequest() {
        DeviceIdInterceptor interceptor = new DeviceIdInterceptor(mock(UserService.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/analyses");
        req.addHeader(DeviceIdInterceptor.HEADER, "not-a-uuid");

        assertThatThrownBy(() -> interceptor.preHandle(req, response, new Object()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void deviceInterceptor_validUuid_upsertsAndStoresUser() {
        UserService userService = mock(UserService.class);
        User user = new User("dev");
        when(userService.getOrCreate(anyString())).thenReturn(user);

        DeviceIdInterceptor interceptor = new DeviceIdInterceptor(userService);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/analyses");
        req.addHeader(DeviceIdInterceptor.HEADER, UUID.randomUUID().toString());

        boolean proceed = interceptor.preHandle(req, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(req.getAttribute(DeviceIdInterceptor.USER_ATTRIBUTE)).isSameAs(user);
    }

    // ---- RateLimitInterceptor ----

    @Test
    void rateLimit_allowsUpToLimitThenBlocks() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(2);
        String device = UUID.randomUUID().toString();

        assertThat(interceptor.preHandle(post(device), response, new Object())).isTrue();
        assertThat(interceptor.preHandle(post(device), response, new Object())).isTrue();

        assertThatThrownBy(() -> interceptor.preHandle(post(device), response, new Object()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void rateLimit_ignoresNonPost() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(1);
        String device = UUID.randomUUID().toString();
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/v1/analyses");
        get.addHeader(DeviceIdInterceptor.HEADER, device);

        assertThat(interceptor.preHandle(get, response, new Object())).isTrue();
        assertThat(interceptor.preHandle(get, response, new Object())).isTrue();
    }

    private MockHttpServletRequest post(String device) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/analyses");
        req.addHeader(DeviceIdInterceptor.HEADER, device);
        return req;
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
