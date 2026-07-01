package com.ilgo.auth;

import com.ilgo.auth.dto.AuthResponse;
import com.ilgo.auth.dto.LoginRequest;
import com.ilgo.auth.dto.LogoutRequest;
import com.ilgo.auth.dto.MeResponse;
import com.ilgo.auth.dto.RefreshRequest;
import com.ilgo.auth.dto.RegisterRequest;
import com.ilgo.common.ApiException;
import com.ilgo.common.ErrorCode;
import com.ilgo.security.JwtProvider;
import com.ilgo.user.ProfileService;
import com.ilgo.user.User;
import com.ilgo.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final ProfileService profileService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, RefreshTokenService refreshTokenService,
                       ProfileService profileService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.profileService = profileService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email() == null ? null : request.email().trim();
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new ApiException(ErrorCode.INVALID_EMAIL);
        }
        if (!StringUtils.hasText(request.password())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "비밀번호를 입력하세요.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = new User(email, passwordEncoder.encode(request.password()));
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException race) {
            // 동시 가입 등으로 unique 충돌
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return issue(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email() == null ? null : request.email().trim();
        // 어느 쪽이 틀렸는지 노출하지 않도록 실패는 모두 INVALID_CREDENTIALS
        User user = (email == null) ? null : userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password() == null ? "" : request.password(),
                user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issue(user);
    }

    /** refresh 토큰을 회전하고 새 access/refresh 를 발급한다. */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String raw = request.refreshToken() == null ? null : request.refreshToken().trim();
        if (!StringUtils.hasText(raw)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(raw);
        User user = rotation.user();
        String access = jwtProvider.generateToken(user.getId(), user.getEmail());
        return AuthResponse.bearer(access, rotation.newRefreshToken(),
                jwtProvider.getAccessExpiresInSeconds(), user.getId().toString(), user.getEmail());
    }

    /** signup = register 별칭(§3-1). 동일하게 계정 생성 후 토큰을 발급한다. */
    @Transactional
    public AuthResponse signup(RegisterRequest request) {
        return register(request);
    }

    /** 세션 확인(§3-3): 인증된 사용자와 프로필을 반환한다. */
    @Transactional(readOnly = true)
    public MeResponse me(User user) {
        return new MeResponse(
                new AuthResponse.UserSummary(user.getId().toString(), user.getEmail()),
                profileService.get(user));
    }

    /** logout: 제출된 refresh 토큰을 폐기한다(멱등). */
    @Transactional
    public void logout(LogoutRequest request) {
        String raw = request.refreshToken() == null ? null : request.refreshToken().trim();
        if (StringUtils.hasText(raw)) {
            refreshTokenService.revoke(raw);
        }
    }

    private AuthResponse issue(User user) {
        String access = jwtProvider.generateToken(user.getId(), user.getEmail());
        String refresh = refreshTokenService.issue(user);
        return AuthResponse.bearer(access, refresh, jwtProvider.getAccessExpiresInSeconds(),
                user.getId().toString(), user.getEmail());
    }
}
