# plan_park_0428_01 — UC-CORE-3 발주 시점 예측 구현 계획

> "지금 발주해도 되나?" — 가격 추이 시계열 + 발주 타이밍 신호 + 발주 이력 저장

---

## 0. 상태

- **작성일**: 2026-04-28
- **순번**: 01
- **상태**: 검토 완료 대기 → 구현 착수 전
- **sim 합의 필요**: ❌ (전 항목 park 단독)
- **근거 문서**: 유스케이스_냉장GOAT v0.3 UC-CORE-3, UC-SUP-8 / project_v03_spec.md

---

## 1. 목표 및 비즈니스 근거

### 왜 UC-CORE-3가 최우선인가

인터뷰에서 발견된 페인포인트 세 가지:

| 페인포인트 | 해결 기능 |
|---|---|
| 가격·시세 수동 비교 (원가율 40~50% 직접 계산) | **UC-CORE-2** ✅ 완료 |
| "지금 사는 게 싼지, 더 기다려야 하는지" 판단 불가 | **UC-CORE-3 ← 여기** |
| 발주 이력이 없어 지난번 얼마에 샀는지 모름 | **UC-CORE-3 + UC-SUP-8 ← 여기** |

UC-CORE-2가 "어디서 얼마에 사야 하나"를 해결했다면,
UC-CORE-3는 **"언제 사야 하나"** 를 해결한다.

---

## 2. 구성 3파트 개요

```
Part A  가격 추이 시계열 API
        ↳ MarketPrice (KAMIS 일별 시세) → 30일 시계열 + 이동평균
        ↳ buySignal: 현재 도매가 < 30일평균 × 0.95

Part B  PurchaseOrder 엔티티 + CRUD API
        ↳ 발주 완료 시 이력 저장 (날짜·재료·수량·단가·금액·거래처·메모)
        ↳ UC-SUP-8: 이력 조회 + 기간별 합계

Part C  발주 신호 (Demo = 응답 내 flag / V1 = 배치 알림)
```

---

## 3. Part A — 가격 추이 시계열 API

### 3-1. 이미 있는 것 (재사용)

| 기존 자산 | 위치 | 재사용 방식 |
|---|---|---|
| `MarketPrice` 엔티티 | `analysis/domain/` | 시계열 데이터 소스 (retailPrice/wholesalePrice/reportedDate) |
| `MarketPriceRepository.findAllByIngredientIdAndReportedDateBetween()` | `analysis/repository/` | 기간 조회 쿼리 재사용 |
| `KamisPriceCalculator` | `pricing/service/` | weekAvg/monthAvg 계산 로직 참고 |
| `PriceController` | `pricing/controller/` | 엔드포인트 추가 위치 |

### 3-2. 새로 만들 것

**`pricing/dto/TrendPointDto.java`**
```java
record TrendPointDto(
    LocalDate date,
    Long wholesalePrice,   // 도매가 (발주 의사결정 기준)
    Long retailPrice,      // 소매가 (참고용)
    Long weekAvg,          // 해당 날 기준 직전 7일 이동평균
    Long monthAvg,         // 해당 날 기준 직전 30일 이동평균
    boolean buySignal      // wholesalePrice < monthAvg × 0.95
) {}
```

**`pricing/service/PriceTrendService.java`**
```
입력: ingredientId, days (기본 30)
처리:
  1. MarketPriceRepository.findAllByIngredientIdAndReportedDateBetween(
       ingredientId, today - days, today) 호출
  2. reportedDate 오름차순 정렬
  3. 각 날짜 포인트마다 직전 7일/30일 슬라이딩 윈도우 평균 계산
  4. buySignal = (각 포인트의 wholesalePrice < 해당 monthAvg × 0.95)
출력: List<TrendPointDto>
```

**`PriceController.java` 엔드포인트 추가**
```
GET /prices/{ingredientId}/trend?days=30
Header: X-User-Id: {userId}
Response: List<TrendPointDto>
```

### 3-3. 의사결정 (park 단독 확정)

| 항목 | 결정 | 이유 |
|---|---|---|
| 기준 가격 | **도매가 (wholesalePrice)** | 발주용 의사결정 = 도매 기준. 소매가는 참고 병기 |
| buySignal 임계값 | **monthAvg × 0.95** (5% 이하) | 시장 평균 대비 5% 이상 저렴하면 "지금이 기회" |
| 데이터 부족 시 (30일 미만) | 가용 기간으로 축소 + `dataCoverage` 필드 반환 | project_v03_spec "기간 짧음 배지" 스펙 반영 |
| Demo 알림 방식 | **응답 내 flag** (`buySignal: true/false`) | 외부 알림 서비스 의존 없이 즉시 시연 가능 |

---

## 4. Part B — PurchaseOrder 엔티티 + API (UC-SUP-8)

### 4-1. 새 모듈: `purchase/`

기존 `order/` 모듈은 **고객 주문(매출)** 처리. 발주(구매)는 다른 도메인 → 신규 모듈 분리.

### 4-2. 엔티티 설계

**`purchase/domain/PurchaseOrder.java`**

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | PK, AUTO_INCREMENT |
| `user` | User (FK) | 점주 |
| `ingredient` | Ingredient (FK) | 발주 재료 |
| `orderedAt` | LocalDate | 발주 날짜 |
| `quantity` | BigDecimal | 발주 수량 |
| `baseUnit` | String | 단위 (g, ml, 개 등) |
| `unitPrice` | BigDecimal | 발주 단가 (원) |
| `totalAmount` | BigDecimal | 총 금액 (quantity × unitPrice) |
| `supplier` | String | 거래처명 |
| `memo` | String | 메모 (nullable) |
| `status` | PurchaseStatus (enum) | PENDING / CONFIRMED / CANCELLED |
| `createdAt` | LocalDateTime | 생성 시각 |

**`purchase/domain/PurchaseStatus.java`**
```java
enum PurchaseStatus { PENDING, CONFIRMED, CANCELLED }
```

### 4-3. API 설계

```
POST   /purchase-orders
         Body: { ingredientId, orderedAt, quantity, baseUnit, unitPrice, supplier, memo }
         Response: PurchaseOrderResponse (저장된 발주 전체 필드)

GET    /purchase-orders?from=&to=&ingredientId=&status=&page=&size=
         기본 기간: 최근 30일
         최대 기간: 1년
         기본 정렬: orderedAt DESC
         CANCELLED 포함 기본 노출 (토글로 숨김은 프론트)

GET    /purchase-orders/summary?from=&to=
         Response:
         {
           totalCount: 15,
           totalAmount: 850000,
           byIngredient: [
             { ingredientName: "돼지고기", count: 3, totalAmount: 450000 },
             ...
           ]
         }
```

### 4-4. Flyway 마이그레이션

**`V003__create_purchase_orders.sql`**
```sql
CREATE TABLE IF NOT EXISTS purchase_orders (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NOT NULL,
  ingredient_id BIGINT       NOT NULL,
  ordered_at    DATE         NOT NULL,
  quantity      DECIMAL(10,3) NOT NULL,
  base_unit     VARCHAR(20)  NOT NULL,
  unit_price    DECIMAL(10,2) NOT NULL,
  total_amount  DECIMAL(12,2) NOT NULL,
  supplier      VARCHAR(100) NOT NULL,
  memo          TEXT,
  status        VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_po_user_date (user_id, ordered_at DESC),
  INDEX idx_po_ingredient  (ingredient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 5. Part C — 발주 신호 상세

### Demo (지금 구현)

`GET /prices/{ingredientId}/trend` 응답의 마지막 포인트(오늘)에 `buySignal: true/false` 포함.

추가로 trend 응답 래퍼에 `currentBuySignal` 요약 필드 포함:
```json
{
  "ingredientId": 3,
  "ingredientName": "돼지고기",
  "currentBuySignal": true,
  "signalReason": "현재 도매가(7,200원)가 30일 평균(8,100원)보다 11.1% 저렴",
  "dataCoverage": 28,
  "points": [ ... ]
}
```

### V1 (후속, 본 plan 범위 외)

- Spring `@Scheduled` 매일 오전 8시 체크
- buySignal = true 인 재료 목록 DB 저장 (`purchase_alerts` 테이블)
- 이후 FCM / 카카오 알림톡 연동

---

## 6. 구현 파일 체크리스트

### Part A (가격 추이)

- [ ] `pricing/dto/TrendPointDto.java` — record
- [ ] `pricing/dto/PriceTrendResponse.java` — 래퍼 (ingredientName, currentBuySignal, signalReason, dataCoverage, points)
- [ ] `pricing/service/PriceTrendService.java` — 슬라이딩 윈도우 계산
- [ ] `pricing/controller/PriceController.java` — `GET /prices/{id}/trend` 추가

### Part B (발주 이력)

- [ ] `purchase/domain/PurchaseStatus.java` — enum
- [ ] `purchase/domain/PurchaseOrder.java` — @Entity
- [ ] `purchase/repository/PurchaseOrderRepository.java` — 기간+재료+상태 조회
- [ ] `purchase/dto/PurchaseOrderRequest.java`
- [ ] `purchase/dto/PurchaseOrderResponse.java`
- [ ] `purchase/dto/PurchaseOrderSummaryDto.java`
- [ ] `purchase/service/PurchaseOrderService.java`
- [ ] `purchase/controller/PurchaseOrderController.java`
- [ ] `db/migration/V003__create_purchase_orders.sql`

### 검증

- [ ] `./gradlew compileJava` 통과
- [ ] `/prices/{id}/trend` 실 응답 확인 (MarketPrice 데이터 필요)
- [ ] `/purchase-orders` POST → GET → summary 흐름 확인

---

## 7. sim 합의 불필요 근거

| 항목 | 근거 |
|---|---|
| Part A (추이 API) | MarketPrice 데이터는 park 영역. sim 크롤러 미관련 |
| Part B (PurchaseOrder) | 신규 테이블. sim 크롤러 INSERT 대상 아님 |
| Part C (buySignal) | 알고리즘 park 단독 결정 |
| V003 마이그레이션 | park 전용 테이블. sim schema 변경 없음 |

→ **전 항목 park 단독 확정, 즉시 구현 착수 가능**

---

## 8. 다음 단계

1. 본 plan 검토 후 구현 지시
2. 구현 순서: Part A → Part B → Part C (신호는 A에 포함)
3. 완료 후 MEMORY.md 갱신 + UC-CORE-3 체크 표시
