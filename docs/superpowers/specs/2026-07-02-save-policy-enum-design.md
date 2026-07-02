# 읽고 — enum 기반 분석 결과 저장 정책 설계

| 항목 | 내용 |
| --- | --- |
| 작성일 | 2026-07-02 |
| 브랜치 | `feat/save-policy-enum` |
| 상태 | 승인됨 → 구현 |

## 0. 배경 / 목표

`POST /v1/analyses`가 `save=true` 기본이라 판독 실패·문서 아님/무관 이미지까지 DB에 저장돼 보관함에 쓰레기 레코드가 남는다. 프론트가 create→delete로 임시 회피 중. 근본적으로 **백엔드가 애초에 저장하지 않도록** 한다.

1. 상태·문서분류를 문자열 비교가 아니라 **enum**으로 분기.
2. **유효 결과만 저장**(판독 실패·문서 아님/무관 미저장, `save=true`여도).
3. 저장 스킵이어도 **분석 결과는 200으로 응답**.
4. 프론트 의존 wire 값 유지, 부득이한 변경은 문서화+통지.

## 1. 현행 기준선

- `AnalysisStatus` enum **이미 존재**: PROCESSING/DONE/FAILED, `@JsonValue` 소문자(`processing|done|failed`).
- `Analysis.docType` = free-form String(length 16), DocClass enum 없음.
- `handleReal`(AnalysisService.java:323): parse-fail → FAILED 저장 **후 422(AI_PARSE_FAILED) throw**; 성공 → 항상 save. `save` 파라미터는 `is_saved` 플래그만 설정(레코드는 항상 insert, 기본 목록은 `savedOnly=true`로 is_saved=false 숨김).
- 컨트롤러 POST: `@ResponseStatus(HttpStatus.CREATED)` 고정(201).
- `new AnalysisResponse(` 호출 지점은 `toResponse` 1곳.
- `Analysis.createdAt` = `@CreationTimestamp OffsetDateTime`(setter 있음).

## 2. 확정 결정 (사용자 승인)

1. **doc_class 필드 추가** — `doc_type`(한글) 유지 + 신규 `doc_class` enum wire `notice|announcement|message|contract|unknown|not_a_document`.
2. **저장 스킵 시 `id: null`**.
3. **parse-fail → 200 + status=failed** (기존 422 제거; 프론트 통지).
4. **save=false → insert 자체 스킵** (기존 is_saved 플래그 동작 대체).

## 3. 설계

### 3.1 DocClass enum (신규 `com.ilgo.analysis.DocClass`)
- 값/wire: `NOTICE(notice)`, `ANNOUNCEMENT(announcement)`, `MESSAGE(message)`, `CONTRACT(contract)`, `UNKNOWN(unknown)`, `NOT_A_DOCUMENT(not_a_document)`. `@JsonValue`로 wire 직렬화.
- `from(String docType)` 단일 매핑점:
  - 고지서 → NOTICE
  - 공지 → ANNOUNCEMENT
  - 메시지 / 문자 / 카톡 → MESSAGE
  - 계약 → CONTRACT
  - 문서아님 / not_a_document → NOT_A_DOCUMENT
  - 기타 / 알 수 없음 / 알수없음 / unknown / other / null / blank / 미인식 → UNKNOWN
- `isSavable()` = {NOTICE, ANNOUNCEMENT, MESSAGE, CONTRACT}.

### 3.2 SavePolicy (단일 지점, 신규 `com.ilgo.analysis.SavePolicy`)
```
shouldPersist(status, docClass, save) = status == DONE && docClass.isSavable() && save
```

### 3.3 handleReal 개편
- parse-fail(parsed==null): 저장 X → `failedResponse(outputMode, lang)` (status=FAILED, id=null, doc_class=unknown, createdAt=now, 빈 cards/risks/actions/replyDrafts) 반환. 기존 FAILED 저장 + 422 throw 제거.
- 성공: applyParsed → status=DONE. `docClass = DocClass.from(docType)`.
  - `SavePolicy.shouldPersist` true → `setSaved(true)`, save, `toResponse(saved)`(id 존재).
  - false(문서아님/미지 or save=false) → 저장 X, transient에 createdAt=now 세팅 후 `toResponse`(id=null).

### 3.4 프롬프트 (AiPrompt.SYSTEM)
- 지시 추가: 글이 없거나 문서가 아닌 이미지(풍경·셀카·음식 등)는 `doc_type:"문서아님"`. → NOT_A_DOCUMENT 신호. (판독 자체 실패는 파서 예외 → status=failed 경로.)

### 3.5 응답 DTO
- `AnalysisResponse`에 `DocClass docClass` 컴포넌트 추가(→ wire `doc_class`). compact canonical 생성자에서 `docClass == null`이면 `DocClass.from(docType)`로 파생(단일 파생점; Jackson 역직렬화/mock에도 자동 적용).
- `toResponse`는 docClass에 null 전달(생성자가 파생).

### 3.6 상태코드
- 컨트롤러 두 POST 핸들러를 `ResponseEntity<AnalysisResponse>`로 전환: `id != null` → 201 CREATED, `id == null` → 200 OK. (`@ResponseStatus` 제거.)

### 3.7 DB / 마이그레이션
- 신규 컬럼 없음. docClass는 docType에서 파생(미저장). 저장 row는 항상 savable class. `is_saved`는 저장 row에서 항상 true. 마이그레이션 없음.

### 3.8 Swagger
- DocClass `@Schema`, AnalysisResponse `doc_class` 노출, POST `@Operation` 설명에 저장 정책 명시.

## 4. 테스트 (§수용기준)
- Unit `DocClassTest`: from 매핑(고지서→NOTICE, 메시지/문자→MESSAGE, 기타/null/unknown→UNKNOWN, 문서아님→NOT_A_DOCUMENT, 미인식→UNKNOWN) + isSavable.
- Unit `SavePolicyTest`: DONE+savable+save=true→true; FAILED→false; UNKNOWN/NOT_A_DOCUMENT→false; save=false→false.
- Integration `AnalysisSavePolicyApiTest`(@MockBean AiGatewayClient, mock-mode=false):
  - 고지서(DONE, save=true) → 201, `doc_class=notice`, id 존재, GET 목록 1건.
  - 기타(DONE) → 200, `doc_class=unknown`, `id=null`, 목록 0건.
  - parse-fail(비-JSON) → 200, `status=failed`, `id=null`, 목록 0건.
  - save=false + 고지서 → 200, `id=null`, 목록 0건.

## 5. 프론트 통지 (wire 변경)
1. `doc_class` 신규 필드(위 6개 값).
2. 저장 스킵/failed 시 `id: null`(후속 액션/삭제 불가 신호).
3. 저장 스킵/failed → HTTP 200(정상 저장은 201 유지).
4. parse-fail: 422 AI_PARSE_FAILED → 200 `status:failed` (analyze 엔드포인트 한정; replies:regenerate는 422 유지).
5. `save=false`는 더 이상 hidden 레코드를 만들지 않음(insert 스킵).

## 6. Non-goals
DB 마이그레이션 없음 · DocClass 미저장(파생) · doc_type wire 유지 · 신규 엔드포인트 없음.

## 7. 수용 기준
- [x] status=failed 결과가 DB 미저장 + GET 목록 제외 — `AnalysisSavePolicyApiTest.parseFailure_*`
- [x] 문서아님/무관(UNKNOWN/NOT_A_DOCUMENT) 미저장 — `notADocument_*`
- [x] 정상(DONE+savable, save=true) 저장 — `savableDocument_isSaved_and201`
- [x] 저장 여부 무관 POST 200(스킵) / 201(저장) 반환 — 컨트롤러 `respond()`
- [x] 분기는 enum(문자열 하드코딩 아님) — `DocClass` + `SavePolicy`
- [x] Swagger enum·정책 반영 + 단위테스트(failed/unknown/done) — `@Operation` 설명 + `DocClassTest`/`SavePolicyTest`

## 8. 구현 결과
- 신규: `DocClass`, `SavePolicy`. 변경: `AnalysisResponse`(doc_class 파생), `AnalysisService`(handleReal 저장 분기 + failedResponse), `AnalysisController`(ResponseEntity 201/200), `AiPrompt.SYSTEM`(문서아님).
- 테스트: `DocClassTest`(5), `SavePolicyTest`(4), `AnalysisSavePolicyApiTest`(4). 전체 102개 그린.
- DB 마이그레이션 없음(docClass 파생, 미저장).
