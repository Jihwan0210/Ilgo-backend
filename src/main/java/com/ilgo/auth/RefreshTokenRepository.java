package com.ilgo.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * rotate 직렬화용: 해당 row 에 비관적 쓰기 락(SELECT ... FOR UPDATE)을 걸어
     * 동일 refresh 로 동시에 여러 rotate 가 성공하는 것(회전=1회용 보장 붕괴)을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** 해당 유저의 '활성(미폐기 & 미만료)' refresh 토큰을 모두 폐기(재사용 감지 시). */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.user.id = :userId and t.revokedAt is null and t.expiresAt > :now")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
