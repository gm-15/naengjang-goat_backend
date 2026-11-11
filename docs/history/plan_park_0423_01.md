# plan_0423_01 — 네이버 쇼핑 API 기능을 KAMIS 중심 3층 구조로 편입

> 이미 구현된 `/shopping/lowest` 를 UC-CORE-2 스펙과 맞게 재정의한다.
> KAMIS를 가격 판단 기준(Tier 1)으로, 네이버·쿠팡은 검색 URL 중계(Tier 2) + 업소용 필터/원·kg 파싱(Tier 3) 보조로 역할 분리.

---

## 0. 상태

- 작성일: 2026-04-23
- 순번: 01
- 상태: **검토 중 — 구현 금지**
- 근거 문서
  - 개발범위_냉장GOAT.md v0.3 (팀원 통합 1차)
  - 유스케이스_냉장GOAT.docx v0.3 — UC-CORE-2, UC-SUP-8
  - 최근 커밋: c51e69e (네이버 쇼핑 최저가 검색 API 연동)

---

## 1. 왜 재정의하는가 (비즈니스 관점)

1-1. 현재 `/shopping/lowest` 는 네이버 소비자용 소포장(500g 선물세트 등) 반환 → 업소용 구매 의사결정과 불일치.
1-2. `sort=sim`(관련도순)로 "최저가" 라벨과 정렬 기준이 모순.
1-3. 팀원 v0.3 UC-CORE-2 **메인 API는 `/prices/lowest-top`** (KAMIS 기반 하락률 정렬). 현 `/shopping/lowest`는 **보조로 강등**되어야 함.
1-4. UC-CORE-2 응답 스펙(`externalLinks[]`)은 **검색 URL 중계 구조**를 전제 — 네이버 API 실시간 상품 리스트가 메인이 되면 스펙 위반.

---

## 2. 재정의 컨셉 — 3층 구조

```
[Tier 1] KAMIS                  ← 가격 판단 Source of Truth
  · weekAvg / monthAvg(v0.3) / todayPrice / dropRatePct
  · Top 5 정렬의 유일한 기준
  · 노출 API: GET /prices/lowest-top (UC-CORE-2 메인, 별도 plan에서 설계)

[Tier 2] 검색 URL 중계          ← 항상 노출, 비용 0
  · 재료명 + "업소용" 자동 삽입한 쿠팡/네이버 검색 결과 URL 생성
  · 클릭 시 업소용 필터링된 페이지로 바로 진입
  · UC-CORE-2 externalLinks[] 필드로 반환

[Tier 3] 네이버 API 참고 상품   ← 옵션, 파싱 성공분만 노출
  · sort=asc + "업소용 kg" 강화 쿼리
  · title에서 무게 파싱 성공 → 원/kg 정규화 후 "참고 단위가" 라벨
  · 파싱 실패 → 원본 lprice만, 또는 해당 행 숨김
  · KAMIS 하락률 계산에는 절대 섞지 않음 (오염 방지)
```

---

## 3. 구현 범위 (본 plan은 shopping/ 모듈 보조화까지만)

### 3-1. shopping/ 모듈 수정

**NaverShoppingClient.java**
- `sort=sim` → `sort=asc` 변경
- 쿼리 템플릿에 `"업소용"` 기본 삽입 (파라미터 `boolean forBusiness=true` 토글 가능)
- 응답 매핑 시 무게 파싱 결과를 DTO에 부착

**NaverShoppingItemDto**
- 필드 추가: `Long unitPriceKrwPerKg` (nullable, 파싱 성공 시만)
- 필드 추가: `Integer parsedWeightGrams` (nullable)
- 필드 추가: `boolean weightParseSucceeded`

**NaverShoppingService**
- `parseWeightFromTitle(String title)` 신규
  - 정규식: `(\d+(?:\.\d+)?)\s*(kg|g|KG|G)`
  - 단일 매칭 성공 → 해당 값 사용
  - 다중 매칭 (예: "500g 300g 세트") → null 반환 (혼란 방지)
  - title 에 `박스`, `세트`, `+`, `선물` 포함 시 → null 반환
- `unitPriceKrwPerKg = lprice * 1000 / weightGrams` (BigDecimal, HALF_UP)

**NaverShoppingController**
- 기존 엔드포인트 `/shopping/search`, `/shopping/lowest` 유지 (관리자/디버깅/Tier 3 용)
- JavaDoc 에 **"UC-CORE-2 보조 — 메인 API는 /prices/lowest-top"** 명시

### 3-2. 범위 외 (다음 plan으로 미룸)

- `GET /prices/lowest-top` 신규 — **plan_0423_02**에서 설계
- `ExternalLinkGenerator` (쿠팡/네이버 검색 URL 생성 유틸) — plan_0423_02
- KAMIS 월평균(30일) 집계 로직 — 팀원 배포 스케줄 확인 후 결정

---

## 4. 파싱 전략 & 실패 처리

| 입력 예시 | 결과 |
|---|---|
| "한돈 목살 5kg 업소용" | 5000g ✅ |
| "삼겹살 구이용 500g" | 500g ✅ |
| "한돈 선물세트 목살 삼구이용 500g+500g" | null ❌ (다중+세트) |
| "녹차먹인돼지 … 500g 300g" | null ❌ (다중) |
| "돼지고기 1.5kg 박스" | null ❌ (박스) |

**정책**: 파싱 실패율이 호출당 50% 초과 지속 시 title 정제 룰 재검토 (로그로 모니터링).

---

## 5. 검색 URL 생성 규칙 (Tier 2 — plan_0423_02에서 본구현, 여기서는 합의만)

```
네이버: https://search.shopping.naver.com/search/all?query={재료명}+업소용&sort=asc
쿠팡:  https://www.coupang.com/np/search?q={재료명}+업소용
```

- URL 인코딩은 `UriComponentsBuilder` 로 처리 (한글 자동 인코딩)
- UC-CORE-2 응답 `externalLinks[]` 의 `source` 문자열 표준
  - `"NAVER_SEARCH"` / `"COUPANG_SEARCH"` / `"NAVER_API_REF"` (Tier 3)

---

## 6. 우선순위

| 단계 | 포함 | 비고 |
|---|---|---|
| Demo 필수 | Tier 1 (KAMIS) + Tier 2 (검색 URL) | 팀원 /prices/lowest-top 완성 필요 |
| Demo 확장 | Tier 3 (네이버 API 파싱 참고값) | 본 plan 범위 — 시간 허용 시 |
| V1 | 쿠팡 파트너스, 다나와식 복수 중계 | 개발범위 V1-03 |

---

## 7. 검토 요청 항목

- [ ] `/shopping/*` 를 사장님 앱에 직접 노출할지, `/prices/lowest-top` 내부 호출용으로만 축소할지
- [ ] v0.3 월 평균가(30일) 계산 시 KAMIS 데이터 부족하면 Tier 3 네이버 참고값을 섞을지 (권장: **섞지 말 것**)
- [ ] "업소용" 키워드를 재료별 커스터마이즈할지 (예: 소고기 → "업소용 1등급")
- [ ] NaverShoppingClient 호출 빈도 제한 필요 여부 (요청 시 매번 호출 vs 5분 캐싱)

---

## 8. 팀원과 합의 필요 (열린 질문)

- `externalLinks[]` 의 `source` 문자열 네이밍 표준 확정
- UC-CORE-2 `/prices/lowest-top` 모듈 배치 (analysis / 신규 pricing / shopping 내부?)
- UC-SUP-8 발주 이력(`PurchaseOrder` 엔티티)이 order 모듈에 들어가는지 신규 모듈인지

---

## 다음 단계

1. 본 plan 검토·승인 (**아직 구현 금지**)
2. 승인 시 `plan_0423_02.md` 에 `/prices/lowest-top` 메인 API + ExternalLinkGenerator 설계 작성
3. plan_0423_01 + plan_0423_02 모두 승인 후 구현 착수
