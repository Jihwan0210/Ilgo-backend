# 읽고 (Ilgo) — Backend API

어려운 한국어 글(고지서·공지·계약·메시지 등) 이미지를 받아 **쉬운 한국어/모국어 풀이 · 위험 신호등 · 할 일 · (외국인용) 답장**으로 바꿔 저장·반환하는 API 서버.

Flutter 앱이 사진 1장을 올리면, 서버가 AI로 분석해 구조화 결과(ReadResult)를 돌려주고 보관함에 저장한다.

```
┌──────────┐  이미지+모드/언어   ┌─────────────────┐  멀티모달 호출  ┌──────────────┐
│ Flutter  │ ─────────────────▶ │   읽고 백엔드     │ ─────────────▶ │  AI Gateway  │
│   앱     │ ◀───────────────── │ (Spring Boot)   │ ◀───────────── │ (OpenAI 호환) │
└──────────┘  구조화 결과(JSON)  └────────┬────────┘  JSON 응답     └──────────────┘
                                          │ 저장/조회
                                     ┌────▼─────┐
                                     │ Supabase │  users · analyses · risks(+steps) · actions · reply_drafts · feedbacks · refresh_tokens
                                     └──────────┘  (PostgreSQL)
```

## 기술 스택

| 레이어 | 기술 |
|---|---|
| 언어/프레임워크 | Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, Security) |
| DB | Supabase (PostgreSQL), HikariCP |
| 인증 | JWT (HS256) access + 회전형 refresh 토큰 |
| AI | OpenAI 호환 게이트웨이(Chat Completions) via `RestClient` |
| API 문서 | springdoc-openapi (Swagger UI) |
| 빌드 | Gradle (wrapper) |
| 배포 | Railway (Dockerfile) |

## 빠른 시작 (로컬)

**요구사항:** JDK 21.

```bash
# 환경변수 (Supabase pooler + JWT + AI). 아래는 예시 — 실제 값으로 교체.
export SPRING_PROFILES_ACTIVE=local
export SPRING_DATASOURCE_URL="jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:6543/postgres?prepareThreshold=0"
export SPRING_DATASOURCE_USERNAME="postgres.<project-ref>"
export SPRING_DATASOURCE_PASSWORD="<db-password>"
export JWT_SECRET="<32바이트 이상 랜덤 문자열>"
export AI_API_KEY="<gateway-key>"      # 없으면 MOCK_MODE=true 로 대체
# export MOCK_MODE=true                # AI 없이 목 응답으로 분석 흐름 확인

./gradlew bootRun
```

서버: `http://localhost:8080`
- 헬스체크: `GET /health` → `{"status":"ok"}`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI 스펙: `GET /v3/api-docs`

> **참고:** Supabase **직접 연결(5432)** 은 IPv6 전용이라 IPv4-only 환경(로컬 Mac, Railway 등)에서는 연결이 안 될 수 있다. **Pooler(6543, transaction mode)** 를 쓰고 URL 뒤에 `?prepareThreshold=0`, username 은 `postgres.<project-ref>` 형식을 사용한다.

## 환경변수

| 변수 | 필수 | 기본값 | 설명 |
|---|:---:|---|---|
| `SPRING_DATASOURCE_URL` | ✓ | — | `jdbc:postgresql://...` (반드시 `jdbc:` 로 시작) |
| `SPRING_DATASOURCE_USERNAME` | ✓ | — | pooler 사용 시 `postgres.<ref>` |
| `SPRING_DATASOURCE_PASSWORD` | ✓ | — | DB 비밀번호 |
| `JWT_SECRET` | ✓ | (개발용 기본값) | HS256 서명 키, **32바이트 이상**. 운영에서 반드시 교체 |
| `JWT_ACCESS_EXPIRES_IN` | | `1800` | access 토큰 수명(초, 기본 30분) |
| `JWT_REFRESH_EXPIRES_IN` | | `1209600` | refresh 토큰 수명(초, 기본 14일) |
| `AI_API_KEY` | | (빈 값) | AI 게이트웨이 키. 없고 `MOCK_MODE=false` 면 분석이 502 |
| `AI_BASE_URL` | | mindlogic 게이트웨이 | OpenAI 호환 Chat Completions 엔드포인트 |
| `AI_MODEL` | | `gemini-3-flash-preview` | 사용 모델 |
| `AI_MAX_TOKENS` | | `4096` | 응답 최대 토큰 |
| `AI_TIMEOUT_SECONDS` | | `30` | AI 호출 read 타임아웃 |
| `MOCK_MODE` | | `false` | true 면 AI 대신 목 JSON 반환 |
| `RATE_LIMIT_PER_MIN` | | `20` | 사용자당 분당 `POST /v1/analyses` 제한 |
| `PORT` | | `8080` | 서버 포트 (Railway 가 주입) |

## API 요약

Base path: `/v1` · 인증: `Authorization: Bearer <access_token>` (auth 엔드포인트 제외)

| Method | Path | 설명 | 인증 |
|---|---|---|:---:|
| POST | `/v1/auth/signup` (=`/register`) | 회원가입 → 토큰 발급 | — |
| POST | `/v1/auth/login` | 로그인 → 토큰 발급 | — |
| POST | `/v1/auth/refresh` | refresh 회전 → 새 access/refresh | — |
| POST | `/v1/auth/logout` | refresh 토큰 폐기(멱등) | — |
| GET | `/v1/auth/me` | 세션 확인(`{user, profile}`) | ✓ |
| GET/PUT | `/v1/profile` | 기본 모드/언어/글씨크기/알림 (PUT 부분수정) | ✓ |
| POST | `/v1/analyses` | **이미지 분석(핵심)** → ReadResult | ✓ |
| GET | `/v1/analyses` | 보관함 목록(커서 페이지네이션) | ✓ |
| GET | `/v1/analyses/{id}` | 분석 단건 조회 | ✓ |
| DELETE | `/v1/analyses/{id}` | 분석 삭제 | ✓ |
| PATCH | `/v1/analyses/{id}/actions/{actionId}` | 할 일 완료 토글 | ✓ |
| POST | `/v1/analyses/{id}/replies:regenerate` | 답장 재생성(native 전용) | ✓ |
| POST | `/v1/analyses/{id}/feedback` | 정확도 피드백 | ✓ |
| GET | `/v1/help-contacts` | 도움받을 곳 목록 | ✓ |
| GET | `/health` | 헬스체크 | — |

JSON 은 전역 **snake_case**. 에러는 `{"error":{"code","message","details?}}` 형식.

### 분석 요청/응답 예시

```jsonc
// POST /v1/analyses
{
  "image_base64": "<BASE64>",
  "media_type": "image/jpeg",       // image/jpeg | image/png, ≤ 5MB
  "output_mode": "easy_korean",     // easy_korean | native
  "lang": "ko",                     // native 면 km/vi/ne/en/...
  "save": true
}
```

```jsonc
// 201 Created (요약)
{
  "id": "...", "status": "done", "output_mode": "easy_korean", "lang": "ko",
  "doc_type": "고지서",
  "summary_one_line": "8월 15일까지 건강보험료 6만 8천 원을 내야 해요",
  "explained_text": "...", "consequence": "...",
  "cards": { "what": "...", "when": null, "where": "...", "amount": "68,000원", "deadline": "2026-08-15" },
  "risks":   [ { "id":"...", "level": "yellow", "type": "기한", "message": "...", "detail": null, "steps": [] } ],
  "actions": [ { "id":"...", "text": "...", "priority": 1, "is_done": false } ],
  "reply_drafts": [],               // native 모드일 때만 채워짐
  "created_at": "..."
}
```

## 데이터 모델

`users` 1─N `analyses` 1─N `{ risks, actions, reply_drafts, feedbacks }` (자식 FK `ON DELETE CASCADE`).
`risks` 1─N `risk_steps`(단계별 대처). `refresh_tokens` 는 회전형 refresh 저장(SHA-256 해시만).

핵심 카드(what/when/where/amount/deadline)는 1:1이라 `analyses` 컬럼으로 보관. 이미지는 기본 미저장(`image_ref=null`).
스키마는 `spring.jpa.hibernate.ddl-auto=update` 로 자동 반영.

## AI 연동

`POST /v1/analyses` 내부에서 서버가 OpenAI 호환 게이트웨이를 1회 호출한다(이미지 base64 data-URI + 지시 텍스트). 응답 JSON 을 파싱해 엔티티로 매핑·저장한다.

- 파서는 코드펜스 제거 + 첫 `{` ~ 마지막 `}` 추출로 관대하게 파싱.
- 파싱 실패 시 1회 재시도, 그래도 실패면 `status=failed` 저장 + `422 AI_PARSE_FAILED`.
- 429 는 백오프 후 1회 재시도, 그 외 게이트웨이 오류/타임아웃은 `502 UPSTREAM_ERROR`.
- `MOCK_MODE=true` 면 AI 호출 없이 `src/main/resources/mock/*.json` 을 반환.

## 엣지케이스

| 상황 | 응답 |
|---|---|
| jpeg/png 아님 | 400 `UNSUPPORTED_MEDIA_TYPE` |
| base64 디코드 실패 | 400 `INVALID_REQUEST` |
| 이미지 > 5MB | 413 `IMAGE_TOO_LARGE` |
| 글자 못 읽음/파싱 실패 | 422 `AI_PARSE_FAILED` (`status=failed` 저장) |
| 문서 아님 | 200 `status=done`, `doc_type=null` (프론트가 "문서 아님" 처리) |
| 답장 재생성인데 easy 모드 | 409 `NOT_NATIVE_MODE` |
| 분당 호출 초과 | 429 `RATE_LIMITED` |
| AI 게이트웨이 오류/타임아웃 | 502 `UPSTREAM_ERROR` |
| refresh 토큰 무효/만료/재사용 | 401 `INVALID_REFRESH_TOKEN` |

## 테스트

```bash
./gradlew test
```
슬라이스(H2 인메모리) + MockMvc 통합 테스트. 인증/분석 매핑/보관함 커서/refresh 회전·재사용 감지/프로필/엣지케이스 커버.

## Docker

```bash
docker build -t ilgo-backend .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://.../postgres?prepareThreshold=0" \
  -e SPRING_DATASOURCE_USERNAME="postgres.<ref>" \
  -e SPRING_DATASOURCE_PASSWORD="<pw>" \
  -e JWT_SECRET="<secret>" \
  -e AI_API_KEY="<key>" \
  ilgo-backend
```

멀티스테이지 빌드(JDK 21 빌드 → JRE 21 실행, non-root). 로컬 시크릿 파일(`application-supabase.yml`)은 이미지에 포함되지 않으므로 배포 시 환경변수로 주입한다.

## 배포 (Railway)

- 빌드: `railway.json` 의 `DOCKERFILE` 빌더 사용.
- 헬스체크 경로: `/health`.
- 위 환경변수를 Railway **Variables** 에 등록(특히 `SPRING_DATASOURCE_URL` 은 `jdbc:` 로 시작 + pooler 사용).
- 변수 변경 또는 main push 시 재배포.

## 프로젝트 구조

```
src/main/java/com/ilgo
├── ai         # 게이트웨이 클라이언트 · 프롬프트 · 응답 파서
├── analysis   # 분석 도메인(엔티티/서비스/컨트롤러/DTO)
├── auth       # 회원가입/로그인/토큰(refresh 회전)
├── user       # 사용자 · 프로필
├── help       # 도움받을 곳
├── security   # JWT 필터/프로바이더
├── common     # 에러 코드/핸들러 · 레이트리밋 · CurrentUser
└── config     # Security · OpenAPI · RestClient · Web
```
