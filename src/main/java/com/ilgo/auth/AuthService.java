package com.ilgo.auth;

import com.ilgo.auth.dto.AuthResponse;
import com.ilgo.auth.dto.LoginRequest;
import com.ilgo.auth.dto.RegisterRequest;
import com.ilgo.common.ApiException;
import com.ilgo.common.ErrorCode;
import com.ilgo.security.JwtProvider;
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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
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

    @Transactional(readOnly = true)
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

    private AuthResponse issue(User user) {
        String token = jwtProvider.generateToken(user.getId(), user.getEmail());
        return AuthResponse.bearer(token, user.getId().toString(), user.getEmail());
    }
}
