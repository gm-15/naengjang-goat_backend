# 개발 일지 — 2026-05-03 (park)

---

## 버그 수정

### 1. KAMIS 배치 `parsed=0` 수정

**증상**  
매일 새벽 배치가 실행되는데 모든 카테고리에서 `parsed=0` 로그 출력. 가격 데이터 수집 안 됨.

**원인**  
- 주말(토/일)에도 직전 날짜 그대로 API 호출 → KAMIS는 주말 데이터 없음  
- 공휴일(5/1 노동절) 대응 없음 → `error_code=200` (데이터 없음) 반환

**수정** (`KamisApiClient.java`)  
- `prevWeekday()`: 주말이면 평일이 나올 때까지 하루씩 뒤로  
- `fetchAllCategories()`: `error_code=200` 응답 시 하루 더 뒤로, 최대 10일 재시도  

```
실행 흐름:
어제 → 주말이면 스킵 → API 호출 → error_code=200이면 하루 더 뒤로 → 반복
최대 10일 이내 가장 최근 평일 데이터 자동 수집
```

---

### 2. N+1 — store_settings 쿼리 반복 수정

**증상**  
재고 부족 조회 시 store_settings 쿼리가 재료 수만큼 반복 실행 (재료 40개 → 쿼리 40번)

**원인**  
`LowStockService`가 재료 루프 안에서 매번 `calcNextOrderDayDistance()` 호출 → 매번 DB 조회

**수정** (`DepletionCalculatorService.java`, `LowStockService.java`)  
- `calculate()` 오버로드 추가: `nextOrderDayDistance`를 외부에서 주입받는 버전  
- 루프 밖에서 1회 계산 후 전달  

```
수정 전: store_settings 쿼리 N번 (재료 수만큼)
수정 후: store_settings 쿼리 1번
```

---

## 신규 기능

### 3. 알림 시스템 완성 (`AlertTriggerService.java`)

기존 알림은 재고 부족만 체크하고, 중복 방지가 없어서 매 분마다 같은 알림이 반복 발송됐음.

**중복 방지**  
- `ConcurrentHashMap<"userId:slot", LocalDate>` 인메모리 저장  
- 슬롯(open/close)별로 날짜가 바뀌기 전까지 1회만 발송  
- 자정이 지나면 날짜 비교로 자동 리셋. Redis SETNX로 먼저 구현했다가 단일 서버에서 오버스펙이라 교체.

**가격 buySignal 알림 추가**  
- 재고 알림 체크와 같은 타이밍(영업 3시간 전)에 가격 급락 재료도 함께 알림  
- `PriceTrendService.getTrend()` 호출 → `currentBuySignal == true` 인 재료 목록 로그 출력  
- `lowStockService.getTopLowStock()` 결과를 재고 알림·가격 알림 두 곳에 재사용 (쿼리 절약)

---

### 4. 재고 단건 입력 API (`POST /inventory/batches`)

기존에 Excel 일괄 업로드만 있었고 JSON 단건 입력 API가 없었음.

**요청**
```json
{
  "ingredientId": 1,
  "quantity": 10.0,
  "costPerUnit": 2500,
  "inboundDate": "2026-05-03",
  "expirationDate": "2026-05-10"
}
```

**응답** `201 Created`
```json
{ "batchId": 42, "quantity": 10.0, "expiresAt": "2026-05-10" }
```

- 소유권 검증: ingredient가 JWT 토큰의 userId 소유인지 확인  
- 유통기한 과거 입력 시 400 반환  
- `inboundDate` 생략 시 오늘 날짜로 자동 처리

신규 파일: `BatchRequest.java`, `InventoryBatchService.java`

---

### 5. 온보딩 API (`POST /api/users/onboard`)

sim이 크롤링한 식자재왕 레시피 68개 메뉴를 가입 직후 점주 계정에 자동 복사.

**흐름**
```
카테고리 선택 (예: ["KOREAN", "WESTERN"])
  → recipe_template 조회 (JOIN FETCH로 BOM 포함 1쿼리)
  → 각 템플릿마다 Menu 생성
  → BOM 재료명으로 기존 Ingredient 검색
      매칭 성공 → 기존 재료 연결
      매칭 실패 → 신규 Ingredient 생성 + newIngredients 목록에 추가
  → RecipeBom 생성 (requiredQuantity=1 placeholder)
```

**요청 / 응답**
```json
// POST /api/users/onboard
{ "categories": ["KOREAN", "WESTERN"] }

// 200 OK
{ "createdMenus": 41, "createdBom": 850, "newIngredients": ["냉동 홍합살", ...] }
```

`requiredQuantity=1`로 생성되는 이유: 식자재왕 BOM은 1인분 소모량이 아닌 상품 패키지 단위(예: 1kg)라 그대로 쓰면 원가 과대계상. 사장님이 직접 수정해야 함.

**Flyway V005** (`recipe_template`, `recipe_template_bom` 테이블 생성)  
신규 파일: `RecipeTemplate.java`, `RecipeTemplateBom.java`, `RecipeTemplateRepository.java`, `OnboardService.java`, `OnboardRequest.java`, `OnboardResponse.java`

---

### 6. Docker 컨테이너화

`Dockerfile` (멀티스테이지)
```
Stage 1 (builder): eclipse-temurin:21-jdk-alpine → ./gradlew bootJar
Stage 2 (runtime): eclipse-temurin:21-jre-alpine → java -jar app.jar
```

`docker-compose.yml`: Spring Boot + MySQL 8.0 + Redis 7  
- MySQL/Redis healthcheck 통과 후 앱 기동 (`depends_on: condition: service_healthy`)  
- 환경변수로 DB URL, Redis 주소 주입

`RedisConfig.java`: 하드코딩된 `redis://127.0.0.1:6379` → `${redis.address}` 프로퍼티로 외부화

```bash
# 전체 실행
docker-compose up --build
```

---

### 7. GitHub Actions CI (`.github/workflows/ci.yml`)

push/PR 시 자동으로 빌드 + 테스트 실행.  
MySQL 8.0, Redis 7을 서비스 컨테이너로 띄워 실제 통합 테스트까지 커버.

```
트리거: push to main/develop, PR to main
JDK: temurin 21
Gradle 캐시: ~/.gradle/caches 캐싱
실행: ./gradlew build --no-daemon
```

---

---

## EKAPE 축산물 가격 통합 (2026-05-04)

### EKAPE API 파라미터 실측 (ekapepia.com)

전 세션에서 시도하던 잘못된 livestockType(4101/4102/4401) 문제 해결.
소비자가격 페이지 HTML 직접 파싱으로 정확한 파라미터 확보.

| 축종 | livestockType | 주요 부위(spec) | grade |
|------|--------------|-----------------|-------|
| 소 | 4301 | 안심=21, 등심=22, 설도=36, 양지=40, 갈비=50 | 03(1등급) |
| 돼지 | 4304 | 앞다리=25, 삼겹살=27, 갈비=28, 목심=68 | 없음 |
| 닭 | 9901 | 육계(kg)=99 | 없음 |

날짜 파라미터: `startDate=YYYY-MM-DD&endDate=YYYY-MM-DD&aggregationUnit=DAY`
일별 응답 날짜 헤더 형식: `"05월 03일"` (연도 없음 → 요청 연도 기준 보정)

### KamisCategory.LIVESTOCK threshold 재보정

EKAPE 소비자가격 6년(2019-2024) 월별 intra-year CV 실측:

| 품목 | CV |
|------|----|
| 소 등심 1등급 | 4.3% |
| 소 양지 1등급 | 2.9% |
| 돼지 삼겹살 | 8.3% |
| 닭 육계(kg) | 3.9% |
| **4품목 평균** | **≈ 4.9%** |

→ LIVESTOCK threshold **0.08 유지** (삼겹살 8.3% 기준, B2B 도매가 변동 더 클 것으로 추정)
→ `KamisCategory.java` 주석 갱신 완료

### 구현 내용

**신규 파일:**
- `batch/ekape/EkapeApiClient.java` — EKAPE HTML 파싱 클라이언트
  - 재료명 키워드 매핑 (13개 키워드 → EKAPE 파라미터)
  - `fetchDailyPrices()`: 일별 데이터 → `Map<LocalDate, Integer>`
- `batch/ekape/EkapeScheduler.java` — 매일 03:30 실행
  - LIVESTOCK 재료 전체 조회 → EKAPE 매핑 → 최근 31일 데이터 수집
  - `existsByIngredientIdAndReportedDateAndSource` 체크로 멱등 보장
- `db/migration/V006__market_price_add_source.sql` — `source` 컬럼 추가

**수정 파일:**
- `MarketPrice.java` — `source` 필드 추가 (DEFAULT 'KAMIS')
- `MarketPriceRepository.java` — `existsByIngredientIdAndReportedDateAndSource` 추가
- `IngredientRepository.java` — `findByKamisCategory` 추가

**동작 흐름:**
```
03:00 KAMIS 배치 → market_price (source='KAMIS', 비축산물)
03:30 EKAPE 배치 → market_price (source='EKAPE', LIVESTOCK만)
→ PriceTrendService: ingredient_id + 날짜 범위로 조회 (source 무관하게 동작)
→ LIVESTOCK 재료도 buySignal 정상 작동
```

---

## 커밋 이력

| 해시 | 내용 |
|------|------|
| `ff4067e` | feat: 알림 개선 + 재고단건입력 + 온보딩 API + Docker/CI |
| `981e06c` | refactor(alert): Redis SETNX → 인메모리 ConcurrentHashMap 교체 |

---

## 남은 작업

| 항목 | 상태 |
|------|------|
| FCM 실제 푸시 발송 | TODO 주석으로 확장 포인트 표시. 데모엔 불필요 |
| 온보딩 BOM requiredQuantity | placeholder(1) — 사장님 직접 수정 필요. 기능 자체는 완성 |
| sim 3건 대기 | `raw_product_id` NOT NULL, TRUNCATE→INSERT IGNORE, `weight_grams` 파싱 — sim 선행 필요 |
