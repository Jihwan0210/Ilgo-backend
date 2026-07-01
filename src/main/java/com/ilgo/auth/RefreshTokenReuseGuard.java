package com.ilgo.auth;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 재사용 감지 시 유저의 활성 refresh 토큰을 모두 폐기한다.
 * rotate() 는 폐기 직후 401 예외를 던져 자신의 트랜잭션을 롤백하므로,
 * 폐기가 함께 롤백되지 않도록 REQUIRES_NEW 로 즉시 커밋되는 별도 트랜잭션에서 실행한다.
 */
@Component
public class RefreshTokenReuseGuard {

    private final RefreshTokenRepository repository;

    public RefreshTokenReuseGuard(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActiveForUser(UUID userId) {
        repository.revokeAllActiveByUserId(userId, OffsetDateTime.now());
    }
}
