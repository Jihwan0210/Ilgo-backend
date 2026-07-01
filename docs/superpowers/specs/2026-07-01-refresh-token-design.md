# Refresh Token — Design Spec

| | |
|---|---|
| Date | 2026-07-01 |
| Feature | Access/Refresh token 분리 + rotating refresh token |
| Scope | `com.ilgo.auth`, `com.ilgo.security`, `com.ilgo.user`, `com.ilgo.common` |
| Depends on | 기존 JWT 이메일/비밀번호 인증 (`AuthService`, `JwtProvider`) |

## 1. 배경 / 목표

현재 인증은 **단일 access JWT (7일 만료)** 만 발급하며 refresh/logout/revocation이 없다.
장수명 access 토큰은 유출 시 만료까지 무효화 불가.

목표: 짧은 access JWT + 긴 refresh 토큰으로 분리하고, refresh를 **DB에 저장(stateful)** 하여
로그아웃/강제만료(revocation)와 회전(rotation) + 재사용 감지를 지원한다.

**결정 사항 (사용자 승인):**
- 저장 방식: **Stateful (DB 저장, revocable)**
- 수명: **access 30분 / refresh 14일**

## 2. 토큰 모델

| | Access | Refresh |
|---|---|---|
| 형식 | HS256 JWT (기존) | Opaque 랜덤 256bit (base64url) |
| 수명 | 30분 (`jwt.access-expires-in:1800`) | 14일 (`jwt.refresh-expires-in:1209600`) |
| 저장 | 무상태 (서명) | DB `refresh_tokens` (SHA-256 해시만) |
| 검증 | 서명 검증 (`JwtProvider.parseUserId`) | 해시 조회 + 만료/revoke 확인 |

Refresh 원문(raw)은 응답으로 1회만 클라이언트에 전달하고 **서버는 SHA-256 해시만 저장**한다.

## 3. 데이터 모델 — `RefreshToken` (table `refresh_tokens`)

기존 ERD 패턴(`@OnDelete CASCADE`, `@Index`, UUID PK, `@CreationTimestamp`)을 그대로 따른다.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | uuid | PK, `@GeneratedValue(UUID)` | |
| user_id | uuid | FK→users.id, NOT NULL, `@OnDelete CASCADE`, index `ix_refresh_tokens_user` | 소유자 |
| token_hash | varchar(64) | UNIQUE (`ux_refresh_tokens_hash`), NOT NULL | SHA-256(raw) hex(64자) |
| expires_at | timestamptz | NOT NULL | 발급 시각 + refresh 수명 |
| revoked_at | timestamptz | NULL | null=활성, 값 존재=폐기됨 |
| created_at | timestamptz | NOT NULL, `@CreationTimestamp`, updatable=false | |

`ddl-auto: update` 가 테이블/인덱스를 자동 생성한다(마이그레이션 파일 불필요).

토큰 유효 = `revoked_at == null && expires_at > now`.

## 4. 엔드포인트

세 경로 모두 `/v1/auth/**` 아래라 `SecurityConfig` 의 기존 `permitAll` 적용됨(보안 설정 변경 불필요).

### POST /v1/auth/refresh
- 요청: `{ "refresh_token": "<raw>" }`
- 성공 200: `{ access_token, refresh_token, token_type:"Bearer", expires_in, user:{id,email} }`
  - `refresh_token` = **회전된 새 값**, `expires_in` = access 수명(초).
- 실패 401 `INVALID_REFRESH_TOKEN`: 미존재/만료/이미 폐기(재사용).

### POST /v1/auth/logout
- 요청: `{ "refresh_token": "<raw>" }`
- 성공 204 (본문 없음). 멱등: 이미 없거나 폐기된 토큰이어도 204.
- 동작: 해당 refresh 토큰만 revoke (access는 30분 뒤 자연 만료).

### register / login (기존, 응답 확장)
- 응답에 `refresh_token`, `expires_in` 필드 추가. 기존 필드 유지 → **하위호환**(필드 추가만).

## 5. 회전 + 재사용 감지 로직 (refresh)

```
raw 입력 → sha256(raw) → 해시로 조회
  없음                      → 401 INVALID_REFRESH_TOKEN
  revoked_at != null        → 재사용(탈취) 의심 → revokeAllForUser(user) → 401
  expires_at <= now         → 401 INVALID_REFRESH_TOKEN
  정상:
    old.revoked_at = now                       (1회용 회전)
    new refresh 발급/저장
    new access 발급
    → 200 { access_token, refresh_token(new), ... }
```

재사용 감지: 이미 회전되어 revoke된 토큰이 다시 제출되면 = 탈취 정황 → 해당 유저의 **활성 refresh 전부 revoke**.

## 6. 구성요소

| 신규/수정 | 파일 | 역할 |
|---|---|---|
| 신규 | `user/RefreshToken.java` (또는 auth 패키지) | entity |
| 신규 | `auth/RefreshTokenRepository.java` | `findByTokenHash`, `revokeAllByUserId` |
| 신규 | `auth/RefreshTokenService.java` | issue / rotate / revoke / revokeAllForUser, 해시·랜덤 생성 |
| 신규 | `auth/dto/RefreshRequest.java`, `LogoutRequest.java` | `{refresh_token}` |
| 수정 | `auth/dto/AuthResponse.java` | `refresh_token`, `expires_in` 필드 추가 |
| 수정 | `auth/AuthService.java` | `issue()`가 refresh도 발급; `refresh()`, `logout()` 추가 |
| 수정 | `auth/AuthController.java` | `POST /refresh`, `POST /logout` 매핑 |
| 수정 | `security/JwtProvider.java` | access 수명 = `jwt.access-expires-in`(30분); `getAccessExpiresInSeconds()` |
| 수정 | `common/ErrorCode.java` | `INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED)` |
| 수정 | `application.yml` | `jwt.access-expires-in`, `jwt.refresh-expires-in` |

랜덤: `SecureRandom` 32바이트 → base64url(패딩 없음). 해시: `MessageDigest SHA-256` → hex 64자.

## 7. 테스트 (TDD, H2 slice/통합)

1. register/login 응답에 `refresh_token` + `expires_in` 포함.
2. refresh 성공 → 새 access + 새 refresh, 이전 refresh는 무효(재사용 시 401).
3. 재사용 감지 → 유저 전체 refresh revoke (다른 활성 refresh도 무효).
4. 만료된 refresh → 401 INVALID_REFRESH_TOKEN.
5. 미존재 refresh → 401.
6. logout → 해당 refresh revoke, 이후 refresh 시 401. logout 멱등(중복 호출 204).
7. DB에 raw 토큰이 저장되지 않음(해시만) 확인.

## 8. YAGNI (범위 제외)

- 만료 토큰 스케줄 정리(`@Scheduled`) — 나중.
- 디바이스별 세션 목록 / logout-all 엔드포인트 — 나중.
- Access 토큰 블랙리스트 — 불필요(30분 단명).

## 9. 위험 / 참고

- Supabase pooler(transaction mode) + JPA: 기존과 동일 경로라 추가 이슈 없음.
- 응답 필드 추가는 하위호환이나, `jwt.expires-in`(7일)→`access-expires-in`(30분) 변경으로
  기존 발급 access 토큰의 **정책 수명이 짧아짐**(기존 토큰 자체는 서명상 7일 유효, 신규만 30분).
- `INVALID_REFRESH_TOKEN` 은 401 매핑(기존 `UNAUTHORIZED` 와 같은 상태코드, 코드 문자열로 구분).
