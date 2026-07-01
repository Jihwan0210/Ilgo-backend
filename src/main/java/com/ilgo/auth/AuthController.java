package com.ilgo.auth;

import com.ilgo.auth.dto.AuthResponse;
import com.ilgo.auth.dto.LoginRequest;
import com.ilgo.auth.dto.LogoutRequest;
import com.ilgo.auth.dto.RefreshRequest;
import com.ilgo.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth", description = "이메일/비밀번호 인증")
@SecurityRequirements // 인증 불필요(로그인/가입/갱신/로그아웃) → Swagger 보안 요구에서 제외
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "회원가입", description = "이메일 형식 검증 후 가입하고 access/refresh 토큰을 발급한다.")
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호 검증 후 access/refresh 토큰을 발급한다.")
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "토큰 갱신",
            description = "refresh 토큰을 회전하고 새 access/refresh 를 발급한다. 유효하지 않으면 401.")
    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "로그아웃", description = "제출된 refresh 토큰을 폐기한다(멱등). 204 반환.")
    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody LogoutRequest request) {
        authService.logout(request);
    }
}
