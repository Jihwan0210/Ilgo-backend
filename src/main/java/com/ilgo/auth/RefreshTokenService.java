package com.ilgo.auth;

import com.ilgo.common.ApiException;
import com.ilgo.common.ErrorCode;
import com.ilgo.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Refresh 토큰 발급/회전/폐기. opaque 랜덤 토큰을 발급하고 DB 에는 SHA-256 해시만 저장한다.
 * 회전(rotation): refresh 시 이전 토큰을 폐기하고 새 토큰을 발급한다.
 * 재사용 감지: 이미 폐기된 토큰이 다시 제출되면 탈취 정황으로 보고 해당 유저의 활성 토큰을 모두 폐기한다.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final RefreshTokenReuseGuard reuseGuard;
    private final SecureRandom random = new SecureRandom();
    private final long refreshExpiresInSeconds;

    public RefreshTokenService(RefreshTokenRepository repository,
                               RefreshTokenReuseGuard reuseGuard,
                               @Value("${jwt.refresh-expires-in:1209600}") long refreshExpiresInSeconds) {
        this.repository = repository;
        this.reuseGuard = reuseGuard;
        this.refreshExpiresInSeconds = refreshExpiresInSeconds;
    }

    /** 새 refresh 토큰을 발급하고 원문(raw)을 반환한다. 서버는 해시만 저장한다. */
    @Transactional
    public String issue(User user) {
        String raw = generateRaw();
        repository.save(new RefreshToken(user, hash(raw),
                OffsetDateTime.now().plusSeconds(refreshExpiresInSeconds)));
        return raw;
    }

    /** raw refresh 를 검증하고 1회용 회전한다. 실패 시 {@link ApiException}(INVALID_REFRESH_TOKEN). */
    @Transactional
    public Rotation rotate(String raw) {
        RefreshToken token = repository.findByTokenHash(hash(raw))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
        OffsetDateTime now = OffsetDateTime.now();

        if (token.getRevokedAt() != null) {
            // 이미 회전되어 폐기된 토큰의 재사용 → 탈취 정황 → 유저 전체 폐기
            // (별도 트랜잭션에서 커밋: 아래 예외로 현재 트랜잭션이 롤백돼도 폐기는 유지)
            reuseGuard.revokeAllActiveForUser(token.getUser().getId());
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        token.setRevokedAt(now); // 회전: 이전 토큰 폐기 (dirty checking 으로 flush)
        User user = token.getUser();
        String newRaw = generateRaw();
        repository.save(new RefreshToken(user, hash(newRaw), now.plusSeconds(refreshExpiresInSeconds)));
        return new Rotation(user, newRaw);
    }

    /** logout: 해당 refresh 토큰을 폐기한다. 없거나 이미 폐기됐어도 조용히 통과(멱등). */
    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(hash(raw)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(OffsetDateTime.now());
            }
        });
    }

    public record Rotation(User user, String newRefreshToken) {
    }

    private String generateRaw() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
