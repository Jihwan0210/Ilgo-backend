## 4. 핵심 API — POST /v1/analyses

이미지 1장을 보내면 분석 결과를 받는다.

**요청 (application/json)**

```json
{
  "image_base64": "<BASE64_STRING>",
  "media_type": "image/jpeg",
  "output_mode": "easy_korean",
  "lang": "ko",
  "save": true
}
```

- `output_mode`: `easy_korean` | `native`
- `lang`: `ko`(쉬운 한국어일 땐 ko) | `en`(모국어=영어 데모) | 기타 ISO 639-1

### 4-A. 응답 예시 — 쉬운 한국어 · 건강보험 고지서 → `mock/analyze_easy_health.json`

```json
{
  "id": "anl_0001",
  "status": "done",
  "output_mode": "easy_korean",
  "lang": "ko",
  "doc_type": "고지서",
  "summary_one_line": "8월 15일까지 건강보험료 6만 8천 원을 내야 해요",
  "original_text": "2026년 7월분 건강보험료 고지서. 본인부담금 68,000원. 납부기한 2026-08-15. 미납 시 연체금이 부과됩니다.",
  "explained_text": "건강보험료를 내라는 종이예요. 💰 6만 8천 원을 8월 15일까지 내면 돼요. 안 내면 돈이 더 붙어요.",
  "consequence": "기한이 지나면 연체료가 더해질 수 있어요",
  "cards": { "what": "건강보험료 납부", "when": null, "where": null, "amount": "68,000원", "deadline": "2026-08-15" },
  "risks": [
    { "id": "rsk_1", "level": "yellow", "type": "기한", "message": "8월 15일이 지나면 연체료가 붙을 수 있어요" }
  ],
  "actions": [
    { "id": "act_1", "text": "은행 앱이나 지로로 6만 8천 원을 내세요", "priority": 1, "is_done": false },
    { "id": "act_2", "text": "낸 뒤 영수증을 보관하세요", "priority": 2, "is_done": false }
  ],
  "reply_drafts": [],
  "created_at": "2026-06-30T07:10:00Z"
}
```

### 4-B. 응답 예시 — 모국어(영어) · 사장님 카톡(임금 위험) → `mock/analyze_native_wage.json`

```json
{
  "id": "anl_0002",
  "status": "done",
  "output_mode": "native",
  "lang": "en",
  "doc_type": "메시지",
  "summary_one_line": "Tomorrow 5 AM meetup. If you're late, your daily pay gets cut.",
  "original_text": "내일 새벽 5시까지 창고 앞으로 집합. 늦으면 일당 깐다.",
  "explained_text": "Your boss says: gather in front of the warehouse tomorrow at 5:00 AM. If you are late, he will deduct part of your daily wage.",
  "consequence": "If you arrive late, you may lose part of tomorrow's pay.",
  "cards": { "what": "Report for work (meetup)", "when": "Tomorrow 05:00", "where": "In front of the warehouse", "amount": null, "deadline": null },
  "risks": [
    { "id": "rsk_2", "level": "red", "type": "임금", "message": "This message says your daily wage will be cut if you are late. Check your work contract." }
  ],
  "actions": [
    { "id": "act_3", "text": "Set an alarm for 4:00 AM to arrive on time", "priority": 1, "is_done": false }
  ],
  "reply_drafts": [
    { "id": "rd_1", "korean": "네, 알겠습니다. 내일 새벽 5시까지 창고 앞으로 가겠습니다.", "note_in_lang": "\"Yes, understood. I'll be in front of the warehouse by 5 AM tomorrow.\"" },
    { "id": "rd_2", "korean": "확인했습니다. 혹시 늦을 수 있어 미리 말씀드립니다.", "note_in_lang": "\"Noted. I'm letting you know in advance in case I might be late.\"" }
  ],
  "created_at": "2026-06-30T07:20:00Z"
}
```

### 4-C. 응답 예시 — 쉬운 한국어 · 과태료(위험 빨강) → `mock/analyze_easy_fine.json`

```json
{
  "id": "anl_0003",
  "status": "done",
  "output_mode": "easy_korean",
  "lang": "ko",
  "doc_type": "고지서",
  "summary_one_line": "9월 1일까지 주차 과태료 4만 원을 내야 해요",
  "original_text": "불법주정차 과태료 부과 사전통지서. 과태료 40,000원. 의견제출 기한 2026-09-01.",
  "explained_text": "주차를 잘못해서 벌금이 나왔어요. 🚗 4만 원이에요. 9월 1일까지 내거나 억울하면 의견을 낼 수 있어요.",
  "consequence": "기한을 넘기면 돈이 더 늘어날 수 있어요",
  "cards": { "what": "주차 과태료", "when": null, "where": null, "amount": "40,000원", "deadline": "2026-09-01" },
  "risks": [
    { "id": "rsk_3", "level": "red", "type": "과태료", "message": "기한을 넘기면 가산금이 붙어요. 억울하면 기한 안에 의견을 낼 수 있어요." }
  ],
  "actions": [
    { "id": "act_4", "text": "9월 1일까지 4만 원을 내세요", "priority": 1, "is_done": false },
    { "id": "act_5", "text": "주차가 억울하면 의견 제출을 알아보세요", "priority": 2, "is_done": false }
  ],
  "reply_drafts": [],
  "created_at": "2026-06-30T07:25:00Z"
}
```

---

## 5. 나머지 API

### 5-1. GET /v1/analyses — 보관함 목록 → `mock/list.json`

`?limit=20&cursor=<opaque>`

```json
{
  "items": [
    { "id": "anl_0001", "doc_type": "고지서", "summary_one_line": "8월 15일까지 건강보험료 6만 8천 원…", "top_risk_level": "yellow", "card_deadline": "2026-08-15", "created_at": "2026-06-30T07:10:00Z" },
    { "id": "anl_0003", "doc_type": "고지서", "summary_one_line": "9월 1일까지 주차 과태료 4만 원…", "top_risk_level": "red", "card_deadline": "2026-09-01", "created_at": "2026-06-30T07:25:00Z" },
    { "id": "anl_0002", "doc_type": "메시지", "summary_one_line": "Tomorrow 5 AM meetup. If late, pay cut.", "top_risk_level": "red", "card_deadline": null, "created_at": "2026-06-30T07:20:00Z" }
  ],
  "next_cursor": null
}
```

### 5-2. GET /v1/analyses/{id} — 상세

응답: §4-A와 동일한 전체 객체.

### 5-3. DELETE /v1/analyses/{id}

성공: `204 No Content` (본문 없음).

### 5-4. PATCH /v1/analyses/{id}/actions/{action_id} — 할 일 체크

요청 `{ "is_done": true }` → 응답 `{ "id": "act_1", "is_done": true }`

### 5-5. POST /v1/analyses/{id}/replies:regenerate — 답장 재생성(native)

요청(선택) `{ "tone": "polite" }` → 응답 `{ "reply_drafts": [ { "id": "rd_3", "korean": "…", "note_in_lang": "…" } ] }`
easy 모드 분석에 요청 시 `409 NOT_NATIVE_MODE`.

### 5-6. GET / PUT /v1/profile — 설정

GET `{ "output_mode": "easy_korean", "lang": "ko" }` / PUT 요청 `{ "output_mode": "native", "lang": "en" }` → 저장값 반환.