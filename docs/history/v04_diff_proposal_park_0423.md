# v0.4 문서 변경 제안 (2026-04-23)

> 개발범위_냉장GOAT v0.3 → v0.4, 유스케이스_냉장GOAT v0.3 → v0.4 개정에 반영할 변경사항 diff.
> 상세 근거와 구현 범위는 [plan_park_0423_02.md](plan_park_0423_02.md) 참조. 본 문서는 팀원(문서 관리자) 전달용.

---

## 0. 개정 요지 (v0.3 → v0.4)

- **기능 2 상세 페이지 신설**: Top 5 재료 클릭 → KAMIS 시세 + 온라인 최저가(네이버 API + 식자재왕) 원/kg 비교 화면
- **외부 소스 전략 확정**: 네이버 공식 API + 식자재왕 크롤링 2소스. 쿠팡·마켓컬리는 V1-03 로 이월
- **price_records 테이블 역할 명시**: 크롤러·API 수집 공용, `ingredient_id` 매핑으로 UC-CORE-2 조회 지원

---

## 1. 개발범위_냉장GOAT.md 변경 diff

### 1-1. §0 개정 요지에 v0.4 섹션 추가 (맨 위)

```markdown
### v0.3 → v0.4 (이번 개정)

- 기능 2 **상세 페이지** 신설: 재료 클릭 시 KAMIS 현재 시세 + 온라인 최저가 비교
- 외부 소스 전략 확정: **네이버 API + 식자재왕 크롤링 2소스**. 쿠팡·11번가·마켓컬리는 V1-03 로 이월
- `price_records` 공용 테이블 도입 (팀원 크롤러 + 박 네이버 수집기 공용)
- Demo In Scope 에 **D-16 재료 상세 페이지** 추가
```

### 1-2. §3.2 기능 2 "시스템이 제공하는 것" 목록 끝에 추가

```markdown
- 재료 카드 클릭 시 **상세 페이지** 진입 (v0.4):
  · KAMIS 현재 시세 상단 고정 (원/kg 기준)
  · 온라인 최저가 섹션: 네이버 API + 식자재왕 크롤링 소스별 원/kg 정규화 비교
  · 최저가 소스에 [최저가] 뱃지 (동률 시 복수)
  · 각 행에 [구매하러 가기] 외부 링크 CTA — 상품 상세 페이지 직결
```

### 1-3. §3.2 확정 사항 끝에 추가

```markdown
- (v0.4) 상세 페이지 외부 소스: **Demo = 네이버 API + 식자재왕 크롤링 2종**. 영속 테이블은 팀원 공용 `price_records`.
- (v0.4) 원/kg 정규화 파싱 실패한 소스는 상세 응답에서 **제외** (UI 혼동 방지).
```

### 1-4. §6 Demo In Scope 표 끝에 추가

```markdown
| D-16 | **재료 상세 페이지(v0.4)** — KAMIS 시세 + 온라인 최저가(네이버·식자재왕) 원/kg 비교 |
```

### 1-5. §6 V1 In Scope — V1-03 문구 구체화

```markdown
| V1-03 | 구매처 크롤링 확장 — **쿠팡·11번가·마켓컬리** (다나와 스타일), 네이버 배치 수집 전환 |
```

### 1-6. §8 미정·검토 필요 사항 — 첫 줄 업데이트

```markdown
~~외부 구매 사이트 링크 전략: 다나와 스타일 복수 링크 vs API 직접 연동 vs 크롤링 — 각 시점/기준 확정 필요.~~
→ **확정(v0.4): Demo 는 네이버 공식 API + 식자재왕 크롤링 2소스. V1-03 에서 쿠팡·11번가·마켓컬리 확장.**
```

---

## 2. 유스케이스_냉장GOAT.docx 변경 diff

### 2-1. 버전 로그 추가

| 버전 | 일자 | 작성자 | 변경사항 |
|---|---|---|---|
| v0.4 | 2026-04-23 | gm-15 (park) | UC-CORE-2 상세 페이지 신설 · 외부 소스 확정(네이버+식자재왕) · `price_records` 연계 |

### 2-2. UC-CORE-2 사후조건 마지막 줄에 추가

```
+ (v0.4) 재료 카드 클릭 시 GET /prices/{ingredientId} 로 상세 페이지에 진입.
+         KAMIS 현재 시세 + 네이버 API + 식자재왕 크롤링 결과를
+         원/kg 정규화하여 비교 노출한다.
```

### 2-3. UC-CORE-2 관련 API 블록에 추가

```
GET /prices/{ingredientId}  → 상세 페이지 (v0.4)
응답: {
  ingredientId, name, unit,
  kamis: { currentPricePerKg, priceDate, weekAvg, monthAvg },
  onlinePrices: [
    {
      source,            // "네이버_축산물", "식자재왕_축산/난류" 등 price_records.source 그대로
      sourceLabel,       // UI 표기용 ("네이버", "식자재왕")
      productName, productUrl, imageUrl,
      price, currency, isDiscount,
      weightGrams, unitPricePerKg,
      isLowest,          // onlinePrices 내 unitPricePerKg 최솟값 여부
      fetchedAt
    }
  ]
}
```

### 2-4. UC-CORE-2 비즈니스 규칙에 BR2-7 ~ BR2-10 추가

```
BR2-7.  (v0.4) 상세 페이지 온라인 소스: Demo = 네이버 API + 식자재왕 크롤링.
         V1-03 = 쿠팡·11번가·마켓컬리 확장.
BR2-8.  (v0.4) "최저가" 뱃지: onlinePrices 중 unitPricePerKg 최솟값 소스에 부착.
         동률이면 복수 부착 가능.
BR2-9.  (v0.4) 원/kg 파싱 실패 (weightGrams = null) 소스는 onlinePrices 응답에서 제외.
BR2-10. (v0.4) is_discount 정의 통일: "정가 대비 할인된 가격에 제공되는 상품".
         네이버 = (hprice > lprice), 식자재왕 = sales.do 진입 상품.
         KAMIS dropRatePct 와는 별개 축 (시장 평균 대비 하락률).
```

### 2-5. §10 트레이스 매트릭스에 추가

| UC | 단계 | 주요 API / 컴포넌트 |
|---|---|---|
| UC-CORE-2 (상세, v0.4) | Demo | `GET /prices/{ingredientId}` · **pricing 패키지 신규** · `price_records` 공용 테이블 · NaverOnlinePriceProvider · SikjajaewangPriceReader · IngredientMatcher · WeightParser |

### 2-6. §11 미정 사항 — "외부 구매처 링크 전략" 항목 삭제 + 새 항목 추가

```
[삭제]  외부 구매처 링크 전략: 다나와 스타일 복수 링크 vs API 직접 연동 vs 크롤링 병행

[추가]  (v0.4) price_records 테이블 TRUNCATE → append-only + UNIQUE(source, url, DATE(fetched_at)) 전환
         — 가격 추이 축적(V1-04) 전제 조건. 팀 합의 필요.
[추가]  (v0.4) price_records 추가 컬럼 승인 여부:
         ingredient_id, weight_grams, unit_price_per_kg, raw_product_id, image_url
[추가]  (v0.4) IngredientMatcher 매칭 실패 row 처리 정책 (Demo: 로그만 / V1: 관리자 큐)
```

---

## 3. 신규 UC 불필요

UC-CORE-2 확장으로 충분하므로 **신규 UC 생성 없이 기존 UC-CORE-2 사후조건·API·비즈니스 규칙만 확장**.

---

## 4. 우선순위 태그

- 개발범위 v0.4 변경: **반영 필수** (Demo In Scope D-16 기준)
- 유스케이스 v0.4 변경: **반영 필수** (BR2-7 ~ BR2-10 이 구현 기준)
- 미정 사항 3건: **팀원 합의 필요** (ALTER TABLE · append-only 전환 · IngredientMatcher 정책)

---

## 5. 합의 체크리스트 (팀원용)

팀원이 아래에 응답 주시면 본 문서·plan 확정 가능:

- [ ] `price_records` ALTER (§1-3-2 제안 5개 컬럼) 승인
- [ ] TRUNCATE → append-only + UNIQUE 전환 동의
- [ ] `source` 네이밍: `플랫폼_<카테고리>` 단일 컬럼 유지 (혹은 분리 의견)
- [ ] `is_discount` 통일 정의 수용 (BR2-10)
- [ ] pricing 모듈 신규 생성 동의 (vs analysis 흡수 의견)
- [ ] 식자재왕 크롤러에 `weight_grams`·`unit_price_per_kg` 파싱 추가 가능 여부
