package com.ilgo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 JWT 생성/검증. claims: sub=userId, email.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expiresInSeconds;

    public JwtProvider(@Value("${jwt.secret:}") String secret,
                       @Value("${jwt.expires-in:604800}") long expiresInSeconds) {
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "jwt.secret 환경변수(JWT_SECRET)가 필요합니다. HS256 은 32바이트 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiresInSeconds = expiresInSeconds;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public String generateToken(UUID userId, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiresInSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** 토큰을 검증하고 userId 를 반환. 실패 시 {@link JwtException}. */
    public UUID parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
