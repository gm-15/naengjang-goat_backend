# plan_park_0423_02 — UC-CORE-2 메인 API + 상세 온라인 최저가 비교 (네이버+식자재왕)

> `GET /prices/lowest-top`(리스트)과 `GET /prices/{ingredientId}`(상세) 두 API를 설계한다.
> 상세 화면은 KAMIS 현재 시세 + 네이버 API + 식자재왕 크롤링 2 소스를 원/kg 정규화해 비교하며, 팀원 `price_records` 스키마에 정렬 맞춘다.

---

## 0. 상태

- 작성일: 2026-04-23 (2차 갱신 — 팀원 크롤러 스키마 반영)
- 순번: 02 (plan_park_0423_01 후속)
- 상태: **검토 중 — 구현 금지**
- 변경 이력
  - v1: 쿠팡+네이버+마켓컬리 3소스 + OnlinePriceSnapshot 신규 엔티티 가정
  - **v2 (현재)**: 네이버 API + 식자재왕 크롤링 2소스 확정, 팀원 `price_records` 테이블 공용, pricing 모듈 확정
- 근거
  - plan_park_0423_01.md — Tier 1·2·3 컨셉
  - 팀원 크롤러 `price_records` DDL·필드 스펙 (2026-04-23 공유)
  - 사용자 제공 UI 시안 — 상세 화면 레이아웃

---

## 1. v1 대비 변경점

| 항목 | v1 | v2 |
|---|---|---|
| 소스 | 네이버 + 쿠팡 + 마켓컬리 (3) | **네이버 API + 식자재왕 크롤링 (2)** |
| Demo Mock | 쿠팡·마켓컬리 Mock 필수 | **Mock 불필요. 전 소스 실연동** |
| 타깃 | B2C (소비자 소포장) | **B2B (업소용) + B2C 참고** |
| 영속화 | OnlinePriceSnapshot 신규 | **팀원 `price_records` 공용** |
| 모듈 | pricing 제안 | **pricing 확정** |

→ "Mock 없이 실연동" 으로 발표 서사가 강화됨.

---

## 2. 두 화면 정의 (UC-CORE-2)

### 2-1. 리스트 — `GET /prices/lowest-top`
- 팀원 v0.3 스펙 유지 (KAMIS 기반 하락률 Top 5)
- 응답: `{ingredientId, name, weekAvg, monthAvg, todayPrice, dropRatePct, trend, externalLinks[]}`
- `externalLinks[]` 의 `source` = `NAVER_SEARCH` / `SIKJAJAEWANG_SEARCH` (검색 URL 중계, Tier 2)
- 외부 호출 없음

### 2-2. 상세 — `GET /prices/{ingredientId}` (v0.4 신규)

UI 시안 기준 응답 스펙 (팀원 `price_records` 연계):

```json
{
  "ingredientId": 17,
  "name": "닭고기",
  "unit": "kg",
  "kamis": {
    "currentPricePerKg": 8900,
    "priceDate": "2026-04-14",
    "weekAvg": 9100,
    "monthAvg": 9050
  },
  "onlinePrices": [
    {
      "source": "네이버_축산물",
      "sourceLabel": "네이버",
      "productName": "한돈 닭고기 1kg",
      "productUrl": "https://smartstore.naver.com/...",
      "price": 8800,
      "currency": "KRW",
      "isDiscount": false,
      "weightGrams": 1000,
      "unitPricePerKg": 8800,
      "isLowest": true,
      "fetchedAt": "2026-04-23T14:00:00Z"
    },
    {
      "source": "식자재왕_축산/난류",
      "sourceLabel": "식자재왕",
      "productName": "업소용 닭고기 5kg",
      "productUrl": "https://www.ewangmart.com/goods/detail.do?gno=...",
      "price": 44000,
      "currency": "KRW",
      "isDiscount": true,
      "weightGrams": 5000,
      "unitPricePerKg": 8800,
      "isLowest": true,
      "fetchedAt": "2026-04-23T14:00:00Z"
    }
  ]
}
```

**규칙**
- `isLowest = true` 는 `unitPricePerKg` 최솟값 소스에 부착 (동률이면 복수 부착)
- `weightGrams = null` (파싱 실패) 소스는 응답에서 **제외** — UI 혼동 방지
- `source` 문자열은 팀원 컨벤션 `플랫폼_<카테고리>` 그대로 사용. `sourceLabel` 은 UI 표기용 플랫폼 단축명

---

## 3. 팀원 `price_records` 스키마 정렬

### 3-1. 팀원 기존 DDL (그대로 수용)

```sql
CREATE TABLE price_records (
  id           INT AUTO_INCREMENT PK,
  source       VARCHAR(100),      -- "식자재왕_채소/과일", "네이버_축산물"
  product_name VARCHAR(255),
  price        INT,               -- 원 단위 정수
  currency     VARCHAR(10),       -- 'KRW'
  is_discount  TINYINT(1),
  product_url  TEXT,
  fetched_at   DATETIME
);
```

### 3-2. 추가 제안 컬럼 — **비즈니스 핵심 위해 필수**

팀원 유의사항 §5-5 에서 확장 가능성을 이미 열어둠. 아래 3개 없으면 "원/kg 비교" 조회가 불가능.

```sql
ALTER TABLE price_records
  ADD COLUMN ingredient_id     BIGINT       NULL COMMENT 'Ingredient.id 매핑 (매칭 실패 시 NULL)',
  ADD COLUMN weight_grams      INT          NULL COMMENT 'product_name 파싱 결과',
  ADD COLUMN unit_price_per_kg BIGINT       NULL COMMENT '원/kg 정규화 (weight null 이면 NULL)',
  ADD COLUMN raw_product_id    VARCHAR(100) NULL COMMENT '소스별 상품ID (네이버 productId, 식자재왕 gno)',
  ADD COLUMN image_url         TEXT         NULL COMMENT '썸네일 URL (UI 시안 필요)',
  ADD INDEX idx_ingredient_fetched (ingredient_id, fetched_at DESC),
  ADD UNIQUE KEY uk_source_url_date (source, product_url(255), (DATE(fetched_at)));
```

**왜 이 컬럼들이 필요한가**

| 컬럼 | 없으면 발생하는 문제 |
|---|---|
| `ingredient_id` | 사장님 재료와 매핑 불가 → UC-CORE-2 상세 조회 불가능 |
| `weight_grams` / `unit_price_per_kg` | 매 조회마다 `product_name` 파싱 → 성능 저하, 과거 추이 분석 시 재파싱 필요 |
| `raw_product_id` | 동일 상품 중복 감지 어려움 (URL 리다이렉트 대응) |
| `image_url` | UI 시안의 상품 썸네일 구현 불가 |
| UNIQUE `(source, url, date)` | 팀원 §5-3 명시 — append-only 누적 필수 조건. TRUNCATE 방식은 가격 추이 불가 |

### 3-3. 네이버 API → `price_records` 필드 매핑

| price_records 필드 | 네이버 API 값 | 가공 로직 |
|---|---|---|
| `source` | `"네이버_" + category2` | 예: `"네이버_축산물"` (팀원 컨벤션 따름) |
| `product_name` | `title` | `<b>` 태그 제거 (`NaverShoppingClient.stripHtml()` 기구현) |
| `price` | `lprice` | `Integer.parseInt()` |
| `currency` | `"KRW"` | 고정 |
| `is_discount` | `hprice != "" && parseInt(hprice) > parseInt(lprice)` | 팀원 §4 공식 그대로 |
| `product_url` | `link` | 그대로 |
| `raw_product_id` | `productId` | 네이버 API 필드 |
| `image_url` | `image` | 그대로 |
| `weight_grams` | `WeightParser.parse(title)` | 실패 시 null |
| `unit_price_per_kg` | `price * 1000L / weight_grams` | `weight_grams == null` 이면 null, `BigDecimal.HALF_UP` |
| `ingredient_id` | `IngredientMatcher.match(product_name, userId)` | 실패 시 null |
| `fetched_at` | `NOW()` | 수집 시점 |

### 3-4. `is_discount` 판정 기준 통일 (팀원 §5-2 답)

- **공통 정의**: "정가 대비 할인된 가격에 제공되는 상품"
- 식자재왕: `sales.do` 진입 상품 → 1
- 네이버: `hprice > lprice` → 1
- KAMIS `dropRatePct` 와 **혼동 금지** — 전혀 다른 축
  - `is_discount`: 플랫폼 내부 할인 여부
  - `dropRatePct`: 시장 평균(KAMIS) 대비 현재값 하락률

---

## 4. `ingredient_id` 매핑 전략

크롤러·API 수집은 `Ingredient` 존재를 모름. `IngredientMatcher` 가 `product_name` 으로 매칭.

### 4-1. 매칭 시점
수집 시점에 매칭 → `price_records` INSERT 시 `ingredient_id` 채움. 실패 row 는 `NULL` 로 저장 (조회 시 제외).

### 4-2. 매칭 로직 단계적 정교화

| 단계 | 로직 | 정확도 | 언제 |
|---|---|---|---|
| Demo | `product_name LIKE '%<ingredient.name>%'` | 낮음 | 빠른 구현 |
| V1 | `ingredient_alias` 별칭 테이블 (돼지고기↔한돈·돈육) | 중간 | 사장님 수동 추가 가능 |
| V2 | 형태소 분석 + 임베딩 유사도 | 높음 | 토큰 비용 고려 |

### 4-3. Demo 단계 간단화
- 사장님 등록 재료 30~50개 이내 → LIKE 단순 매칭으로 80% 커버 기대
- 매칭 실패 row 는 "관리자 수동 매칭 큐" 로 노출 (V1) / Demo 에서는 로그만

---

## 5. 모듈 배치 — `pricing/` 신설 확정

```
src/main/java/com/naengjang_goat/inventory_system/
  pricing/                                    ← 신규
    controller/
      PriceController.java                    # /prices/lowest-top, /prices/{id}
    service/
      LowestTopService.java                   # KAMIS 하락률 Top 5
      PriceDetailService.java                 # KAMIS + onlinePrices 머지
      OnlinePriceAggregator.java              # Provider 결과 취합·최저가 판정
      IngredientMatcher.java                  # product_name → ingredient_id
    provider/
      OnlinePriceProvider.java                # 인터페이스
      NaverOnlinePriceProvider.java           # NaverShoppingClient 래핑, price_records 저장
      SikjajaewangPriceReader.java            # 팀원 크롤러가 저장한 row 조회 only
    repository/
      PriceRecordRepository.java              # price_records JPA
    domain/
      PriceRecord.java                        # 엔티티
    util/
      WeightParser.java                       # 공용 (plan_park_0423_01 참조)
      SearchUrlBuilder.java                   # Tier 2 검색 URL 생성
    dto/
      LowestTopItemDto, PriceDetailDto, OnlinePriceDto

  shopping/                                   ← 유지, 역할 축소
    NaverShoppingClient.java                  # pricing/provider 에서 의존
    (Controller / Service 는 관리자 디버깅용으로 유지 or 삭제)
```

**데이터 흐름**
```
[식자재왕 크롤러 (팀원 담당)]                  [네이버 수집기 (박 담당)]
         │                                              │
         └──────────┬───────────────────────────────────┘
                    ↓
             price_records 테이블
                    ↑
          PriceRecordRepository
                    ↑
            PriceDetailService  ← IngredientMatcher, WeightParser
                    ↑
      GET /prices/{ingredientId}
```

---

## 6. 네이버 수집 방식

### Demo — On-Demand 방식 (권장)
1. 사장님이 상세 진입 → `PriceDetailService.get(ingredientId)`
2. `PriceRecordRepository` 에서 `ingredient_id + source=NAVER_* + fetched_at >= NOW - 30min` 조회
3. hit → 반환
4. miss → `NaverOnlinePriceProvider.fetch(ingredient.name + " 업소용")` → `price_records` INSERT → 반환
5. 30분 TTL 이 지나면 DB 레벨에서 자동으로 "만료" — UNIQUE 인덱스 `uk_source_url_date` 가 일 1건 보장

### V1 — 배치 수집 전환
- 식자재왕처럼 일 N회 등록 재료 전체 수집
- 과거 추이 데이터 누적 → V1-04 가격 추이 차트 데이터원

---

## 7. Demo 구현 순서 (D1 ~ D7)

| 단계 | 내용 | 담당 |
|---|---|---|
| D1 | `price_records` 컬럼 확장 + UNIQUE 인덱스 (§3-2) | 팀원 + 박 합의 후 박 적용 |
| D2 | 식자재왕 크롤러가 `weight_grams`·`unit_price_per_kg`·`raw_product_id` 저장하도록 확장 | 팀원 |
| D3 | `WeightParser`, `SearchUrlBuilder`, `IngredientMatcher` 공용 유틸 | 박 |
| D4 | `PriceRecord` 엔티티 + `PriceRecordRepository` | 박 |
| D5 | `NaverOnlinePriceProvider` — On-Demand + `price_records` 저장 | 박 |
| D6 | `GET /prices/lowest-top` (KAMIS 하락률 + `externalLinks`) | 박 |
| D7 | `GET /prices/{ingredientId}` (KAMIS + `onlinePrices` 머지) | 박 |

**V1**
- 네이버 배치 수집 전환
- `ingredient_alias` 별칭 테이블
- 관리자 수동 매칭 화면
- 가격 추이 그래프 (V1-04, 데이터는 이미 `price_records` 에 누적)
- V1-03: 쿠팡·11번가·마켓컬리 확장

---

## 8. v0.4 문서 변경 요청

별도 파일 [v04_diff_proposal_park_0423.md](v04_diff_proposal_park_0423.md) 로 정리 — 팀원(문서 관리자)에게 전달용.

---

## 9. 열린 질문 — 팀원 합의 필요

- [ ] `source` 네이밍: `플랫폼_<카테고리>` 단일 컬럼 유지 (vs `platform` + `category` 분리, 팀원 §5-1)
- [ ] `price_records` TRUNCATE → **append-only + UNIQUE 전환** (팀원 §5-3) — 가격 추이 필수 조건
- [ ] §3-2 제안 ALTER TABLE 승인 여부 (ingredient_id, weight_grams, unit_price_per_kg, raw_product_id, image_url)
- [ ] `is_discount` 정의 §3-4 안 수용 여부
- [ ] `IngredientMatcher` 매칭 실패 row 처리 — 로그만 vs 관리자 큐

---

## 10. 다음 단계

1. 본 plan + [v04_diff_proposal_park_0423.md](v04_diff_proposal_park_0423.md) 팀원 리뷰
2. §3-2 ALTER · §9 열린 질문 합의 완료
3. D1 부터 순차 구현 착수 — **합의 전 구현 금지**
4. 팀원 크롤러가 새 컬럼에 데이터 저장 시작하면 D3 ~ D7 내 작업 합류
