# plan_park_0426_01 — sim 합의 응답 검토 + 3대 보완안 + 4일간 의사결정 흐름

> 2026-04-23 ~ 04-26 사이 진행된 UC-CORE-2 설계 흐름(park ↔ sim) 전체를 정리하고,
> sim 의 합의 응답에 대한 박의 절충안 3가지(GENERATED COLUMN · BR2-11 fallback · 액체 밀도 상수)를 확정 제안한다.

---

## 0. 상태

- 작성일: 2026-04-26
- 순번: 01
- 상태: **검토 중 — sim 회신 대기**. 합의 완료 시 plan_park_0423_02 + v04_diff 갱신
- 성격: **의사결정 기록 + 후속 합의 제안**. 구현 지시 문서 아님

---

## 1. 4일간 의사결정 타임라인

### 📅 2026-04-23 (월) — 한 날에 5단계 압축 진행

#### Phase 1 (오전) — park 자기 구현 비판 (비즈니스 관점 검토)

**진입 상황**: 직전 커밋 `c51e69e` 로 네이버 쇼핑 API 연동 완료. park가 사용자에게 "비즈니스 관점에서 유효한지" 검토 요청.

**제안한 3가지 문제점**

| # | 문제 | 근거 |
|---|---|---|
| 1 | B2C 소비자 소포장 데이터 → 식당(B2B)과 미스매치 | 검색 결과: "삼겹살 500g 선물세트" 위주 |
| 2 | `sort=sim` (관련도순) → "최저가" 라벨과 정렬 모순 | NaverShoppingClient.java:45 |
| 3 | KAMIS 단위(원/kg)와 네이버 단위(원/팩) 불일치 | 비교 자체가 불가능 |

**채택된 컨셉 — 3층 구조**

```
Tier 1  KAMIS 가격         = 판단 Source of Truth
Tier 2  검색 URL 중계      = 항상 노출, 비용 0 (업소용 키워드 자동 삽입)
Tier 3  네이버 API 참고가  = 옵션, 원/kg 파싱 성공분만
```

**효과**: "엔지니어 관점" → "사장님 관점" 사고 전환. **이미 구현된 기능을 폐기하지 않고 역할 재정의**.

---

#### Phase 2 (오전 중) — 팀원 v0.3 통합본 도착

**sim 산출물**
- 개발범위_냉장GOAT.md v0.3
- 유스케이스_냉장GOAT.docx v0.3
- v0.3 신규: 월 평균가 표시, 3선 꺾은선 그래프, UC-SUP-8 발주 이력

**핵심 발견**: 팀원 v0.3 명세상 UC-CORE-2 메인 API 는 `GET /prices/lowest-top` (KAMIS 중심). 박의 `/shopping/lowest` 는 **보조로 강등** 되어야 함.

**산출 → plan_park_0423_01.md** — shopping/ 모듈 보조화 계획
- `sort=sim → sort=asc` 변경
- 쿼리에 `"업소용"` 자동 삽입
- title 무게 파싱 → 원/kg 정규화
- shopping/ 모듈 역할 축소

**효과**: 자기 구현 폐기 없이 팀원 스펙에 흡수, 작업 충돌 회피.

---

#### Phase 3 (오후) — UI 시안 도착 → plan_park_0423_02 v1

**사용자 제공**: 닭고기 상세 화면 시안
```
KAMIS 8,900원/kg
─────────────────
쿠팡 8,500/kg [최저가] [구매하러 가기]
네이버 8,800/kg       [구매하러 가기]
마켓컬리 9,200/kg     [구매하러 가기]
```

**핵심 변화**: Tier 3 (원/kg 파싱)가 **옵션 → 필수** 로 승격
- 상세 화면 자체가 정규화된 원/kg 비교를 전제
- "구매하러 가기" CTA 가 검색 결과가 아닌 **특정 상품 페이지 직결**

**산출 → plan_park_0423_02.md v1**
- `GET /prices/{ingredientId}` 신규 제안
- 3소스(쿠팡/네이버/마켓컬리) 가정
- OnlinePriceSnapshot 신규 엔티티 설계

---

#### Phase 4 (저녁) — 4대 검토 항목 토의 → 식자재왕 채택

**박이 제기한 4가지**

| # | 이슈 | park 결정 |
|---|---|---|
| 1 | v0.4 개정 (시안 반영) 진행 여부 | ✅ 진행 |
| 2 | 쿠팡/마켓컬리 ToS 미검토 | **❌ 폐기 → 식자재왕 + 네이버로** |
| 3 | pricing/ vs analysis/ 모듈 배치 | ✅ pricing/ 신설 |
| 4 | OnlinePriceSnapshot Demo vs V1 | ✅ Demo부터 도입 |

**가장 큰 의미 — #2 식자재왕 채택**

식자재왕 = B2B 업소용 platform → **Phase 1 에서 박이 첫 비판한 B2C/B2B 미스매치를 근본 해결**.

| 소스 | 대상 | 구매 단위 |
|---|---|---|
| 쿠팡·마켓컬리 | 일반 소비자 | 500g 선물세트 등 |
| **식자재왕** | **업소(식당)** | **5kg/10kg 덕용** |

**효과**: Demo 시연 시 "Mock 없이 실연동" 주장 가능. 발표 임팩트 ↑.

---

#### Phase 5 (저녁 늦게) — sim의 price_records 스키마 공유

**sim 산출물**
- price_records 테이블 DDL (id, source, product_name, price, currency, is_discount, product_url, fetched_at)
- 필드 스펙 + 네이버/쿠팡 API 매핑 가이드
- 5개 미정 항목 (네이밍·discount·중복·통화·확장 컬럼)

**박의 대응 — plan_park_0423_02 v2 전면 갱신**
- OnlinePriceSnapshot 엔티티 신설 안 → **price_records 공용** 으로 변경
- price_records 에 5개 컬럼 추가 제안:
  - `ingredient_id` (FK), `weight_grams`, `unit_price_per_kg`, `raw_product_id`, `image_url`
- TRUNCATE → append-only + UNIQUE 전환 동의 (팀원 §5-3 명시 요청)

**산출 추가 → v04_diff_proposal_park_0423.md** — 개발범위·유스케이스 v0.3→v0.4 diff (sim 전달용)

---

### 📅 2026-04-24 ~ 25 (화·수) — sim 검토 (외부)

---

### 📅 2026-04-26 (오늘)

#### Phase 6 — sim 합의 응답 도착

**합의 결과**: 6항목 중 4 전면동의 + 2 조건부 수정

**조건부 수정 1 — `unit_price_per_kg` 컬럼 제외 제안**
- sim 이유: 단순 계산식, 중복 저장 회피, 앱단/VIEW 처리 충분
- 박 우려: **INDEX 불가** → 정렬·집계 쿼리 풀스캔, V1-04 가격 추이 시 CPU 부담

**조건부 수정 2 — UNIQUE key `product_url` → `raw_product_id` 변경**
- sim 이유: URL 변동 가능 (쿼리스트링·리다이렉트), 내부 ID가 안정적
- 박 평가: **완전 타당**, 단 `NOT NULL` 제약 추가 필요

**추가 제안 (sim) — `IngredientMatcher` 를 크롤러에서 분리해 백엔드 배치로**
- sim 이유: 관심사 분리, 크롤러 도메인 의존 차단
- 박 평가: **강력 동의**, 단 배치 주기 명시 + on-demand fallback 추가 필요

**추가 정보**: weight_grams 파싱 성공률 sim 예상 ~75% → onlinePrices 25% 빈 영역 발생 우려

---

#### Phase 7 — 박의 3대 보완 제안 (본 plan §3)

§2 합의 응답 검토 결과 도출된 절충안·신규 제안 3건. 다음 섹션에서 상세.

---

## 2. 산출 문서 인벤토리

### 박(park) 작성

| 파일 | 위치 | 작성일 | 내용 |
|---|---|---|---|
| plan_park_0423_01.md | worktree 루트 | 04-23 | shopping/ 모듈 보조화 (sort=asc, 업소용, 원/kg 파싱) |
| plan_park_0423_02.md | worktree 루트 | 04-23 | UC-CORE-2 메인 API + 상세 (v2: 식자재왕+네이버, price_records 정렬) |
| v04_diff_proposal_park_0423.md | worktree 루트 | 04-23 | 개발범위·유스케이스 v0.3→v0.4 변경 diff (sim 전달용) |
| **plan_park_0426_01.md (본 문서)** | worktree 루트 | 04-26 | sim 회신 검토 + 3대 보완안 + 흐름 정리 |

### 팀원(sim) 작성

| 파일/메시지 | 작성일 | 내용 |
|---|---|---|
| 개발범위_냉장GOAT.md v0.3 | 04-23 | 3기능 + 가격 시각화 + 발주 이력 |
| 유스케이스_냉장GOAT.docx v0.3 | 04-23 | UC-CORE-1/2/3 + UC-SUP-1~8 |
| price_records 크롤러 스키마 (메시지) | 04-23 | DDL + 필드 스펙 + API 매핑 가이드 |
| v0.4 변경 제안 합의 응답 (메시지) | 04-26 | 6항목 합의 + 작업 분담 |

### 메모리 (Claude 세션)

| 파일 | 내용 |
|---|---|
| feedback_plan_file_naming.md | `plan_park_MMDD_NN` 명명 규칙 + 상단 2줄 요약 |
| project_v03_spec.md | v0.3 확정 사항 (UC-CORE-2/3, UC-SUP-8 신규 API) |

---

## 3. 본 plan 의 3대 보완 제안

### 3-1. GENERATED COLUMN (STORED) — sim 의 컬럼 제외 제안에 대한 절충

**배경**

| 입장 | 주장 | 합리성 |
|---|---|---|
| sim | `unit_price_per_kg` 컬럼 제외 (앱단/VIEW 계산) | 중복 저장 회피, 일관성 ✅ |
| park | 컬럼 유지 (물리 저장) | INDEX 가능 → 성능 ✅ |

→ **양쪽 다 일리 있음**. 둘 다 충족시키는 절충 필요.

**제안 SQL**
```sql
unit_price_per_kg BIGINT GENERATED ALWAYS AS (
  CASE WHEN weight_grams > 0
       THEN price * 1000 / weight_grams
       ELSE NULL END
) STORED
```

**작동 원리**
- DB가 `INSERT/UPDATE` 시점에 자동 계산 후 디스크 저장
- 사람이 직접 `INSERT`/`UPDATE` 시도 → DB 가 거부 (ERROR 3105)
- price/weight_grams 변경 시 자동 재계산
- STORED → 물리 저장이라 INDEX 가능

**역할 (4가지)**

| 역할 | 설명 |
|---|---|
| 데이터 정합성 | 잘못된 원/kg 값이 DB 레벨에서 차단됨 |
| 조회 성능 | INDEX 가능 → 정렬·필터 쿼리에서 풀스캔 회피 |
| 앱 코드 단순화 | 백엔드 계산 로직 0줄, 버그 가능성 ↓ |
| sim 우려 해소 | "사람 INSERT 로 인한 불일치" 위험을 DB 가 원천 차단 |

**JPA 설정**
```java
@Column(name = "unit_price_per_kg", insertable = false, updatable = false)
private Long unitPricePerKg;
```

**효과 비교**

| 항목 | 컬럼 저장 | 컬럼 제외 (sim) | **GENERATED STORED (제안)** |
|---|---|---|---|
| INDEX·정렬 성능 | ✅ | ❌ | ✅ |
| 데이터 정합성 | ⚠️ (사람 실수 가능) | ✅ | ✅ (DB 보장) |
| 스토리지 | 8 byte/row | 0 | 8 byte/row |
| 제약 | 없음 | 없음 | MySQL 5.7+ |

→ **MySQL 5.7+ 만 충족하면 양쪽 우려 해소**.

---

### 3-2. BR2-11 fallback CTA — 빈 화면 회피

**배경**: sim 의 weight_grams 파싱 성공률 예상 75% → onlinePrices 가 **25% 케이스에서 비어 보임**.

**빈 화면이 뜨는 원인 3종**

| 원인 | 빈도 | 설명 |
|---|---|---|
| A. 크롤링 실패 | 가끔 | ToS 차단·네트워크 장애 |
| **B. 파싱 실패** | **자주** | "선물세트", "혼합박스", 무게 미표기 등 → weight_grams = NULL |
| C. IngredientMatcher 매칭 실패 | 초기 자주 | 사장님 "돼지고기" ↔ 크롤링 "한돈 돈육 특수부위" |

→ **B 가 가장 빈번 (sim 예상 25%)**. 크롤링 정상이어도 빈 화면.

**제안 BR2-11**

> onlinePrices 가 모두 비어있을 때 응답에 `externalSearchLinks` (네이버·식자재왕 검색 URL) 포함, UI 에서 "가격 확인 불가 + 직접 검색" 대체 노출

**UI 비교**

| BR2-11 없음 | BR2-11 있음 |
|---|---|
| 텅 빈 영역 → 사장님 "고장났나?" | "ⓘ 업소용 가격을 가져오지 못했어요" + [네이버 검색][식자재왕 검색] 버튼 |

**효과**
- 25% 케이스에서 "고장난 화면" → "직접 찾기 가능 화면"
- 검색 URL 은 plan_park_0423_01 Tier 2 의 `SearchUrlBuilder` **재사용** → 추가 구현 비용 거의 0
- 응답 DTO 에 `externalSearchLinks: [{source, url}]` 필드 1개 추가만

**API 응답 예시**
```json
{
  "ingredientId": 17,
  "name": "혼합 견과류",
  "kamis": { ... },
  "onlinePrices": [],
  "externalSearchLinks": [
    {"source": "NAVER_SEARCH", "url": "https://search.shopping.naver.com/...?query=혼합견과류+업소용"},
    {"source": "SIKJAJAEWANG_SEARCH", "url": "https://www.ewangmart.com/search?q=혼합견과류"}
  ]
}
```

---

### 3-3. 액체류 밀도 상수 — Demo 부터 적용

**배경**: 액체(L/mL) 단위 재료를 1.0 g/mL 로 처리하면 가격 왜곡

**임팩트 표** (1L 를 1000g 처리 시)

| 재료 | 실제 밀도 | 1L → 처리 후 무게 | 원/kg 오차 |
|---|---|---|---|
| **꿀** | 1.40 g/mL | 1000g (실제 1400g) | **+40% 과대평가** |
| **액젓** | 1.25 g/mL | 1000g (실제 1250g) | +25% 과대평가 |
| **간장** | 1.20 g/mL | 1000g (실제 1200g) | **+20% 과대평가** |
| 우유 | 1.03 g/mL | 1000g (실제 1030g) | +3% (오차 작음) |
| 식용유 | 0.92 g/mL | 1000g (실제 920g) | -8% 과소평가 |
| 참기름 | 0.92 g/mL | 1000g (실제 920g) | -8% 과소평가 |
| 물 | 1.00 g/mL | 1000g | 0% (기준) |

→ **간장·꿀·액젓이 사장님 발주 의사결정을 20~40% 왜곡**. 발표일 시연에서 그대로 노출됨.

**제안 — `LiquidDensity` 상수 클래스 (5분 작업)**
```java
public final class LiquidDensity {
    private static final Map<String, Double> DENSITY = Map.ofEntries(
        Map.entry("간장",   1.20),
        Map.entry("액젓",   1.25),
        Map.entry("식용유", 0.92),
        Map.entry("참기름", 0.92),
        Map.entry("우유",   1.03),
        Map.entry("꿀",     1.40)
    );

    public static double resolve(String productName) {
        return DENSITY.entrySet().stream()
            .filter(e -> productName.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(1.0);   // 매칭 안 되면 물 기준
    }
}
```

**WeightParser 통합**
```java
if (unit == ML) {
    weightGrams = (int) Math.round(value * LiquidDensity.resolve(productName));
}
```

**효과**
- 간장·꿀이 데모에 등장해도 가격 왜곡 없음
- V1 마이그레이션: 상수 → DB 테이블 (`liquid_density`) + 관리자 UI, 1시간 작업

**Demo 부터 도입 이유**
- 코딩 공수: **5분**
- 시연 임팩트: 발표일에 간장 +20% 가격이 그대로 노출되면 시연 신뢰도 손상
- V1 까지 미루면: 간장이 들어간 모든 메뉴(예: 양념 갈비)의 발주 추천이 왜곡

---

## 4. sim 에게 회신할 박의 입장 (요약)

| sim 질문 / 제안 | 박 응답 |
|---|---|
| Q1 unit_price_per_kg 컬럼 제외? | **조건부 반대** — GENERATED STORED 절충안 (§3-1) |
| Q2 UNIQUE `raw_product_id` 기반? | **동의** — 단 `raw_product_id NOT NULL` 강제 |
| Q3 IngredientMatcher 배치 분리? | **동의** — 단 10분 주기 + on-demand fallback 추가 |
| (추가 A) BR2-11 | 전 소스 비어있을 때 fallback CTA (§3-2) |
| (추가 B) 액체 밀도 | Demo 부터 상수 반영 (§3-3) |

---

## 5. "왜 - 무엇을 - 효과" 한눈에

### 4일간 변경 흐름 정리

| 날짜 | 단계 | 제안자 | 무엇을 제안 | 왜 | 효과 |
|---|---|---|---|---|---|
| 04-23 | Phase 1 | park | 3층 구조 (KAMIS·검색URL·API참고가) | 기존 구현이 B2C·정렬·단위에서 비즈니스 부적합 | 기능 폐기 없이 역할 재정의 |
| 04-23 | Phase 2 | sim → park | shopping/ 모듈 보조화 | UC-CORE-2 메인은 /prices/lowest-top (KAMIS) | 작업 충돌 회피 |
| 04-23 | Phase 3 | UI 시안 | Tier 3 옵션 → 필수 | 상세 화면이 원/kg 비교 전제 | 파싱 우선순위 ↑ |
| 04-23 | Phase 4 | park | 쿠팡·마켓컬리 → 식자재왕 | ToS 미검토 + B2C 미스매치 | Mock 없이 실연동, 비즈니스 fit |
| 04-23 | Phase 5 | park | price_records 5컬럼 추가 | 비즈니스 핵심 (원/kg 비교) 위해 필수 | UC-CORE-2 조회 가능해짐 |
| 04-26 | Phase 6 | sim | unit_price_per_kg 제외 / UNIQUE raw_product_id / 매칭 배치 분리 | 정합성·안정성·관심사 분리 | 4 동의 + 2 조건부 |
| 04-26 | Phase 7 | park | GENERATED STORED + BR2-11 + 액체 밀도 | sim 우려·UX 회복·시연 보호 | 양측 우려 동시 해소 |

---

## 6. 다음 단계

1. 본 plan 자기 검토 → sim 에게 §4 의 3+2 항목 회신
2. sim 합의 시:
   - **plan_park_0423_02.md 갱신** — GENERATED COLUMN, NOT NULL 제약, 매칭 배치 주기 (10분), BR2-11, 액체 밀도 반영
   - **v04_diff_proposal_park_0423.md 갱신** — BR2-11 (파싱 실패 fallback), BR2-12 (액체 밀도) 추가
   - **팀원 크롤러 작업** 분담 4건 시작 (sim)
   - **박 백엔드 작업** 5건 착수
3. **본 plan 자체는 의사결정 기록용** — 구현 지시 문서 아님. 수정 자체 거의 발생 안 함

---

## 7. 합의 완료 시 즉시 갱신 대상 문서

| 문서 | 갱신 항목 |
|---|---|
| plan_park_0423_02.md | §3-2 ALTER TABLE: GENERATED STORED 적용, raw_product_id NOT NULL, image_url 포함 4개 컬럼 / §6 매칭 주기 10분 + on-demand fallback |
| v04_diff_proposal_park_0423.md | §2-4 BR2-11, BR2-12 추가 / §5 합의 체크리스트 완료 표시 |
| project_v03_spec.md (메모리) | v0.3 → v0.4 진행 중 표기, BR2-11/12 등 명시 |
