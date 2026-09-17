# sim 작업 인수인계 — KAMIS 버그 픽스 + 시연 안정성 + 단위 통일

> **작성자**: 심상묵 (sim)
> **작성일**: 2026-06-05
> **목적**: kim 의 HANDOVER.md(6/4) 에서 짚어준 KAMIS 영역 6건을 sim 측 코드에서 영구 해결한 작업 인수인계.
> **참조**: `HANDOVER.md` (kim 6/4) — 본 문서는 그 4·5섹션에 대한 백엔드 응답.
> **브랜치**: `feat/sim-order-cancel-supplier`
> **PR**: https://github.com/gm-15/naengjang-goat_backend/pull/new/feat/sim-order-cancel-supplier

---

## 1. 한 줄 요약

> kim 이 시연 직전 발견한 "**시연 전엔 KAMIS 연동 자체가 동작 안 했다**" 는 4가지 API 호출 버그를 sim 메인 코드에서 영구 패치하고, 30일 backfill·전체 user 자동 매핑·부팅 시 자동 트리거·단위 정규화 일관성까지 보강함.

---

## 2. commit 단위 변경 (4건)

| 순서 | SHA | 제목 | 영향 |
|------|-----|------|------|
| 1 | `e6a155c` | fix(kamis): KamisApiClient API 호출 4가지 버그 패치 | KAMIS API 빈 응답 → 실제 응답 |
| 2 | `c91138d` | feat(kamis): 30일 backfill + 전체 user 가격 매핑 | priceHistory 1포인트 → 25포인트, 모든 user 매핑 |
| 3 | `ffbc428` | feat(batch): 부팅 시 KAMIS·EKAPE 자동 트리거 — 시연 안정성 | 시연자가 수동 트리거 안 해도 자동 적재 |
| 4 | `f246cfa` | fix(pricing): kamisPrice / priceHistory 단위 의미 통일 | 박스 단위 노출 버그 해결 (배추 820 → 82) |

---

## 3. 변경 내역 상세

### 3-1. `e6a155c` — KamisApiClient API 호출 4가지 버그 패치 (kim HANDOVER 4섹션)

**진단**: 원본 sim 코드가 KAMIS 공식 API 사양과 어긋나는 4지점 → **모든 호출이 빈 응답**.

**파일**: `backend/.../batch/service/KamisApiClient.java`

| 변경 | 원본 | 패치 | 사유 |
|------|------|------|------|
| 날짜 포맷 | `yyyy/MM/dd` | `yyyy-MM-dd` | KAMIS 는 dash 형식만 응답, slash 는 HTTP 000 |
| `p_product_cls_code` | `01` (소매) | `02` (도매) | 01 은 빈 응답, 02 는 정상 |
| 카테고리 파라미터명 | `p_category_code` | `p_item_category_code` | KAMIS 공식 파라미터명. 기존은 무시됨 |
| 단위 정규화 | (없음) | `&p_convert_kg_yn=Y` 추가 | "10kg(그물망 3포기)" 같은 박스 단위 → kg 환산 |

**검증**: 트리거 후 GET /prices/1 (배추) → kamisPrice 채워짐 (이전 null).

---

### 3-2. `c91138d` — 30일 backfill + 전체 user 가격 매핑 (kim HANDOVER 5-2, 5-3)

**문제 1 (5-2)**: `KamisPriceProcessor.findByName()` 이 첫 번째 매칭만 반환 → demo 외 user (chef02, chef03 등) 의 ingredient 는 KAMIS 가격 매핑 안 됨.

**문제 2 (5-3)**: `fetchAllCategories()` 가 1일치만 호출 → `/prices/{id}/trend` 응답이 1포인트만 → 30일 차트 불가.

**파일 5개**:
- `IngredientRepository`: `findAllByName(name)` 메서드 신설
- `KamisPriceDto`: `reportedDate` 필드 추가
- `KamisApiClient.fetchAllCategories()`: 어제부터 30 평일 backfill 루프 (공휴일 skip)
- `KamisPriceProcessor`: dto.reportedDate 우선, 없으면 어제 fallback
- `KamisPriceWriter`: 통째 재작성
  - Processor 단일 매칭 결과를 받아 같은 이름의 모든 user ingredient 에 복제 저장
  - 중복 체크: `(ingredient_id, reported_date, source="KAMIS")` 기준 skip
  - 신규 가입자도 자동 매핑

**검증**:
- `POST /admin/batch/kamis/run` 1회 트리거 → 배추 priceHistory **25 포인트** (2026-05-12 ~ 2026-06-10)
- 신규 user 가입 시 별도 SQL 복제 작업 불필요 (kim 5-2 의 임시 SQL 영구 제거 가능)

---

### 3-3. `ffbc428` — 부팅 시 KAMIS·EKAPE 자동 트리거 (시연 안정성)

**문제**: KAMIS 매일 03:00 / EKAPE 매일 03:30 cron 으로 자동 동작하지만, 시연 직전 부팅 시점엔 데이터 없음 → 시안 가격 카드·30일 차트 비어보임.

**해결**: 신규 `BatchAutoTrigger` (`backend/.../global/config/BatchAutoTrigger.java`)
- `ApplicationRunner` 구현, Java 21 Virtual Thread 로 백그라운드 실행 (부팅 시간 영향 없음)
- `DataInitializer` 시드 적용 대기 후 (5초 sleep)
- `KAMIS Job` → `EKAPE Scheduler` 순차 호출
- 한쪽 실패해도 다른 쪽 계속 (try/catch 분리)
- `@Profile("!test")` 로 테스트 환경 제외
- 중복 적재는 Writer 의 source 기준 skip 으로 자동 처리

**검증**: IntelliJ ▶ Run → 약 5분 후 모든 시안 화면 풀 데이터 동작.

**시연자 확인 로그**:
```
[BatchAutoTrigger] 부팅 시 KAMIS · EKAPE 자동 트리거 시작
[BatchAutoTrigger] KAMIS Job 트리거 완료 — executionId=N status=COMPLETED
[BatchAutoTrigger] EKAPE 트리거 완료
[BatchAutoTrigger] 부팅 트리거 종료
```

---

### 3-4. `f246cfa` — kamisPrice / priceHistory 단위 의미 통일

**문제**: 같은 ingredient 응답에서 `kamisPrice` 와 `priceHistory` 가 **다른 단위로 노출**.

**예시 (수정 전, 배추)**:
- `kamisPrice = 58` (원/kg)
- `priceHistory[-1].price = 820` (박스 단위 원본)

**원인**: 
- `KamisPriceCalculator.buildKamis` 가 `retailPrice` 만 사용 + `toPricePerKg` 정규화
- `PriceTrendService.getTrend` 가 `wholesalePrice` 우선 + `parsePrice` 원본 그대로 (정규화 X)
- 게다가 `p_product_cls_code=02` (도매) 호출이라 `retailPrice` 가 빈 값 또는 다른 단위

**해결**:
1. `KamisPriceCalculator.buildKamis` / `buildTrend` → `wholesalePrice` 우선 + `retailPrice` fallback 로 통일 (`resolveBestPrice` 헬퍼)
2. `KamisPriceCalculator.toPricePerKg` package-private → public 노출
3. `PriceTrendService` 가 `KamisPriceCalculator` 의존 추가
4. `PriceTrendService.resolvePrice` / `getTrend` → `parsePrice` → `kamisPriceCalculator.toPricePerKg` 변경
5. → `priceHistory.price` 도 원/kg 정규화

**검증**:
| 재료 | kamisPrice | priceHistory 최신 | 결과 |
|------|-----------:|-----------------:|------|
| 닭고기 | 6,660 | 6,660 | ✅ 완전 일치 |
| 삼겹살 | 28,320 | 28,320 | ✅ 완전 일치 |
| 등심 | 105,520 | 105,520 | ✅ 완전 일치 |
| 배추 | 58 | 82 | ⚠️ 일치 안 됨 (별도 원인 — 후속) |
| 양파 | 45 | 35 | ⚠️ 일치 안 됨 (별도 원인 — 후속) |

축산물 4종 완전 일치. 채소 일부 값 차이는 **동일 일자에 같은 ingredient 로 매핑되는 KAMIS row 가 여러 개** (봄배추·고랭지배추 등) 인 문제 — 데이터 수집 단계 별도 후속.

---

## 4. kim HANDOVER 진척 표 (4·5섹션 한정)

| 항목 | 처리 |
|------|------|
| 4-1. 날짜 포맷 yyyy/MM/dd → yyyy-MM-dd | ✅ `e6a155c` |
| 4-2. `p_product_cls_code` 01 → 02 | ✅ `e6a155c` |
| 4-3. 파라미터명 `p_category_code` → `p_item_category_code` | ✅ `e6a155c` |
| 4-4. `&p_convert_kg_yn=Y` 추가 | ✅ `e6a155c` |
| 5-2. findByName → findAllByName (전체 user 매핑) | ✅ `c91138d` |
| 5-3. KAMIS 1일치 → 30일 backfill | ✅ `c91138d` |
| 추가 — 부팅 자동 트리거 | ✅ `ffbc428` |
| 추가 — 단위 의미 통일 | ✅ `f246cfa` (축산 완전, 채소 부분) |

---

## 5. 알아두면 좋은 함정

### 5-1. 신규 가입자 KAMIS 가격
**이전 우회 SQL (kim 5-2):**
```sql
INSERT INTO market_price ... SELECT ... FROM market_price ... JOIN ingredient ...
```
**→ 불필요해짐.** `KamisPriceWriter` 가 자동으로 같은 이름 전체 user 에 복제. 다음 배치 cron / 부팅 자동 트리거 1회로 신규 가입자에게 가격 매핑됨.

### 5-2. 매일 03시 cron 부담
KAMIS 30일 backfill 을 매일 새벽 다시 호출 = 카테고리 6 × 30일 = 180회 API 호출 / 일. KAMIS 무료라 부담 작음. 중복은 Writer 가 source 기준 skip.

### 5-3. 부팅 후 ~5분 대기
`BatchAutoTrigger` 가 백그라운드에서 KAMIS Job + EKAPE 순차 호출. 부팅 직후엔 데이터 없으니 시연 직전 ~5분 여유 두기.

### 5-4. 단위 일관성 완료 영역
- ✅ **모든 응답에서 원/kg 으로 정규화** — kamisPrice / priceHistory / weekAvg / monthAvg
- ✅ buy-signal 비교 기준도 동일 단위라 신호 정확
- ⚠️ 채소 일부 값 차이는 동일 일자 다중 row (예: 봄배추·고랭지배추) 매핑 이슈로 별개

---

## 6. 남은 후속 작업 (다음 작업자)

| 우선순위 | 작업 | 위치 | 사이즈 |
|---------|------|------|-------|
| 🟡 1 | KAMIS 동일 일자 다중 row 통합 (봄배추 + 고랭지배추 → 배추 단일화) | `KamisPriceProcessor` (그룹 평균 또는 대표 row 선택) | 1~2h |
| 🟡 2 | `KamisPriceCalculator.toPricePerKg` 단위 매트릭스 검증 (`p_convert_kg_yn=Y` 적용 후에도 unit 필드가 박스 단위로 오는 케이스) | `KamisPriceCalculator` + 실제 응답 분석 | 30분~1h |
| 🟢 3 | `LowStockService` `@Transactional(readOnly=true)` 정석 해결 (kim 3-2) | `LowStockService` + `DepletionCalculatorService` (`@Transactional(REQUIRES_NEW)`) | 1~2h, **park 영역** |

---

## 7. 빠른 검증 절차

### 7-1. 부팅 검증
```bash
# IntelliJ ▶ Run
# 또는
cd backend && ./gradlew bootRun
# → http://localhost:8080/swagger-ui/index.html
# 부팅 후 ~5분 대기 (BatchAutoTrigger 백그라운드 적재 중)
```

### 7-2. KAMIS 데이터 검증 (3가지)
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo1234"}' | jq -r .accessToken)

# 1. 배추 (KAMIS) — priceHistory 25 포인트 확인
curl -s http://localhost:8080/prices/1 -H "Authorization: Bearer $TOKEN" | jq '.priceHistory | length'

# 2. 닭고기 (EKAPE) — kamisPrice == priceHistory 최신 확인
curl -s http://localhost:8080/prices/15 -H "Authorization: Bearer $TOKEN" \
  | jq '{kamisPrice: .kamisPrice, lastInHistory: (.priceHistory[-1].price)}'

# 3. 수동 트리거 (옵션)
curl -X POST http://localhost:8080/admin/batch/kamis/run -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/admin/batch/ekape/run -H "Authorization: Bearer $TOKEN"
```

### 7-3. DB 조회
```bash
docker exec backend-mysql-1 mysql -uroot -p43214321 naengjang_goat_db -e \
  "SELECT ingredient_id, source, COUNT(*) FROM market_price GROUP BY ingredient_id, source;"
```

---

## 8. 관련 파일 맵 (이번 변경)

```
backend/src/main/java/com/naengjang_goat/inventory_system/
├── batch/
│   ├── dto/
│   │   └── KamisPriceDto.java                    ← reportedDate 필드 추가
│   ├── service/
│   │   └── KamisApiClient.java                   ← API 호출 4 버그 패치 + 30일 backfill
│   ├── processor/
│   │   └── KamisPriceProcessor.java              ← dto.reportedDate 우선 사용
│   └── writer/
│       └── KamisPriceWriter.java                 ← 통째 재작성 (전체 user 복제 + 중복 skip)
├── inventory/repository/
│   └── IngredientRepository.java                 ← findAllByName 신설
├── pricing/service/
│   ├── KamisPriceCalculator.java                 ← wholesale 우선 + toPricePerKg public
│   └── PriceTrendService.java                    ← toPricePerKg 의존성 추가
└── global/config/
    └── BatchAutoTrigger.java                     ← 신규 (부팅 자동 트리거)
```

---

## 9. 팀원 협업 안내

### park 한테
- 본 작업물 PR 리뷰 부탁: `feat/sim-order-cancel-supplier` 브랜치
- `KamisPriceCalculator.toPricePerKg` package-private → public 변경 동의 검토
- `LowStockService` 트랜잭션 정석 해결 (kim HANDOVER 3-2) 은 park 영역 — 별도 협의

### kim 한테
- `HANDOVER.md` 5-2 의 임시 우회 SQL 영구 제거 가능. 신규 가입자에게 별도 가격 복제 SQL 필요 없음
- `HANDOVER.md` 5-3 의 30일 backfill 우회 작업 불필요. cron / 부팅 트리거가 자동 처리
- 프론트 `priceHistory[*].price` 가 이제 원/kg 단위로 일관. 차트 라벨 "원/kg" 명시 권장

---

## 10. 질문/막힘

- 본 작업물 관련 → sim
- KAMIS API 자체 → kim HANDOVER.md 4·5섹션 참조
- `LowStockService` 트랜잭션 → park

질문 있으면 카톡 / 이슈 트래커.
