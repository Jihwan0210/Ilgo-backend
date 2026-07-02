# 읽고 — 사기/스미싱 감지 백엔드 설계

| 항목 | 내용 |
| --- | --- |
| 작성일 | 2026-07-02 |
| 브랜치 | `feat/smishing-scam-detection` |
| 원본 요구사항 | 세션 프롬프트 (읽고 사기/스미싱 프론트 요구사항 기반 BE 요구사항) |
| 상태 | 승인됨 → 구현 |

## 0. 원칙

- 목데이터 응답을 새로 만들지 않는다. 실제 `POST /v1/analyses` 분석이 사기/스미싱을 기존 `Analysis` 스키마로 반환한다.
- DB 대형 변경 없음. `risks.type`(varchar 16) / `risks.level` / `detail` / `steps` / `actions[]` 재사용.
- 서버는 URL을 방문/실행하지 않는다. 텍스트 패턴으로만 판단한다.

## 0.1 확정된 결정 (사용자 승인)

1. **help-contacts 스펙 id 채택** — `hlp_1..hlp_4` → `moel_1350`/`immigration_1345`/`nhis_1577_1000`/`police_112`, `kisa_118`(118) 신규 추가. category 한국어(사기/임금/외국인/건강보험/긴급). §10 프론트 계약(첫 항목 = 추천 CTA)에 맞춤.
2. **에러 코드 유지** — 기존 `UNSUPPORTED_MEDIA_TYPE(400)`/`IMAGE_TOO_LARGE(413)`/`AI_PARSE_FAILED(422)`/`RATE_LIMITED(429)`/`UPSTREAM_ERROR(502)` 사용. 스펙의 `INVALID_IMAGE`/`AI_PROVIDER_ERROR`는 추가하지 않음(기능 동일).
3. **help-contacts 인증 유지** — Bearer 필수(현행). 토큰 없으면 401.
4. **risk.type 서버 정규화** — free-form 유지하되 synonym map으로 canonical 화. 미지의 값만 `기타`. 사기족(피싱/보이스피싱/사칭)→`사기`, 임금족(급여/일당)→`임금`. help-contacts 정렬과 동일 분류기 공유.

## 1. 현행 구조 (기준선)

- `POST /v1/analyses`: `AnalysisController` → `AnalysisService.analyze` → (mock | real). real은 `AiGatewayClient.complete`(system=`AiPrompt.SYSTEM`) → `AiResponseParser` → `AiAnalysis` → `applyParsed`(AnalysisService.java:364) → `Risk`/`Action` 엔티티 → `toResponse` → `AnalysisResponse`.
- `AiPrompt.SYSTEM`(AiPrompt.java:13-38): risk type 열거는 `사기|임금|계약|기한|과태료|기타` 6개. 스미싱은 규칙 문장(L33)에만 느슨히 언급.
- `risk.type`: `Risk.java:44` varchar(16), enum/CHECK 없음. 9개 값 모두 저장 가능. Flyway 없음(`ddl-auto=update`).
- `GET /v1/help-contacts`: `HelpContactController`(lang/doc_type/region 받되 서비스가 무시) → `HelpContactService.CONTACTS` 정적 4개. `kisa_118` 없음.
- 로깅: `original_text` INFO 로그 없음(현행 준수). 파싱 실패 시 raw 응답 WARN 2000자 truncate(`snippet()` AnalysisService.java:484).
- mock: `selectMockFile`(AnalysisService.java:296) — native→wage, easy+hint(과태료/주차/벌금/fine)→fine, else health.

## 2. Module A — 분석 품질 (`POST /v1/analyses`)

엔티티/DTO/응답 스키마 변경 없음.

### A1. `AiPrompt.SYSTEM` 강화 (AiPrompt.java:13-38)
- risk type 열거 확장: `사기|스미싱|임금|계약|부당공제|기한|과태료|건강보험|기타`.
- BE-AN-04 스미싱 규칙 블록 삽입:
  - 공식기관/금융/택배/공공 사칭 + 낯선/단축/오타 도메인 + 앱설치·개인정보·금전 유도 + "오늘까지/즉시/압류/계정정지" 압박 → `red` 또는 `yellow`.
  - 확신 없으면 단정 금지 → "사기/스미싱이 의심돼요. 공식 채널로 확인하세요".
  - actions 후보: 링크 미클릭 / 공식 앱·대표번호·홈페이지 직접 확인 / 118 상담 / 이미 눌렀으면 비밀번호·카드·계좌 입력 금지.
  - 금액·날짜는 원문에서만 추출. 없는 사실 금지.
  - 스미싱류 `summary_one_line`은 "사기/스미싱일 수 있다"가 드러나게.
  - 사기성 문자는 가급적 `사기`로 통일(스펙 AN-05).

### A2. `RiskTypes` 정규화 유틸 (신규 `com.ilgo.analysis.RiskTypes`)
- canonical Set: `사기, 스미싱, 임금, 계약, 부당공제, 기한, 과태료, 건강보험, 기타`.
- `normalize(String) → String`: null/blank/미지 → `기타`; synonym map으로 canonical 매핑.
  - 사기족: 피싱/보이스피싱/사칭/smishing/phishing/scam/fraud → `사기`; 스미싱 → `스미싱`.
  - 임금족: 급여/일당/체불/임금 → `임금`.
  - 계약족: 숙소비/공제 → `계약`; 부당공제 → `부당공제`.
- `family(String) → RiskFamily`(FRAUD/LABOR/CONTRACT/HEALTH/FINE/OTHER): help-contacts 정렬용.
- 적용 지점: `AnalysisService.applyParsed`(L381) — `new Risk(level, RiskTypes.normalize(r.type()), ...)`.

## 3. Module B — help-contacts (`GET /v1/help-contacts`)

### B1. `HelpContact` 데이터 (HelpContactService.CONTACTS)
| id | title | org | phone | category | emergency |
| --- | --- | --- | --- | --- | --- |
| kisa_118 | 스미싱·해킹 상담 | KISA/보호나라 | 118 | 사기 | false |
| moel_1350 | 임금·노동 상담 | 고용노동부 | 1350 | 임금 | false |
| immigration_1345 | 외국인종합안내센터 | 법무부 | 1345 | 외국인 | false |
| nhis_1577_1000 | 건강보험 문의 | 국민건강보험공단 | 1577-1000 | 건강보험 | false |
| police_112 | 긴급 · 범죄 신고 | 경찰 | 112 | 긴급 | true |

### B2. Controller (HelpContactController.java:24)
- `@RequestParam(name="risk_type", required=false) String riskType` 추가 → `service.list(lang, docType, region, riskType)`.
- `@Parameter`/`@Operation` 갱신으로 Swagger에 `risk_type` 노출.

### B3. `list(...riskType)` 정렬 (§HC-02)
- `RiskTypes.family(riskType)` 기준 우선 연락처를 앞으로:
  - FRAUD(사기/스미싱/피싱/보이스피싱) → [kisa_118, police_112] 먼저.
  - LABOR/CONTRACT(임금/급여/일당/부당공제/계약/숙소비/공제) → [moel_1350, immigration_1345] 먼저.
  - HEALTH(건강보험) → [nhis_1577_1000, kisa_118] 먼저.
  - FINE(과태료/기한) → 일반 목록(긴급 아님 우선).
  - OTHER/없음/미지 → 기본 순서 그대로(폴백, 200).
- phone null 항목은 반환에서 제외(현재 전부 phone 보유). lang 미지원 → ko 기본(현재 ko-only).

## 4. Module C — 안전/로깅 (§6)
- `original_text` INFO 로그 없음 유지.
- `snippet()`(AnalysisService.java:484)에 PII 마스킹 추가: 주민번호(`\d{6}-\d{7}`), 카드(`\d{4}-\d{4}-\d{4}-\d{4}`), 계좌/인증번호류 장수열 마스킹 후 truncate.

## 5. Module D — 테스트 (§8)
- Unit `RiskTypesTest`: 보이스피싱→사기, 급여→임금, 계약→계약, 부당공제→부당공제, null/garbage→기타; family 매핑.
- Unit/Api `HelpContactsApiTest` 확장: `risk_type=사기`→items[0].id=kisa_118; `스미싱`→kisa_118; `임금`→moel_1350; `계약`→moel_1350 & immigration_1345 포함; 미지 risk_type→200 기본; phone null 직렬화 안전.
- Integration `AnalysisApiMockModeTest` 확장: hint=스미싱/사기 → 신규 `mock/analyze_easy_smishing.json`; type∈{사기,스미싱}, level=red, actions에 링크미클릭/공식확인/118 중 ≥2.
- mock 픽스처 신규 + `selectMockFile` hint 분기(스미싱/사기/smishing/phishing).
- §8 프롬프트-eval 4케이스는 실 AI 필요 → 문서로만 명시.

## 6. Non-goals
DB 마이그레이션 없음 · 신규 에러코드 없음 · 인증 변경 없음 · URL 방문/스캔 없음 · 신규 엔드포인트 없음.

## 7. 수용 기준 (§9)
- [ ] 스미싱성 이미지 → risks red/yellow, type∈{사기,스미싱}
- [ ] actions에 링크 미클릭 + 공식 채널 확인 포함
- [ ] risk detail/steps 프론트 상세에서 사용 가능
- [ ] `help-contacts?risk_type=사기` 첫 항목 118(kisa_118)
- [ ] `help-contacts?risk_type=임금` 첫 항목 1350(moel_1350)
- [ ] 미지 risk_type → 200 + 기본 목록
- [ ] 원문 전체 운영 로그 미기록
- [ ] Swagger에 risk_type 반영
