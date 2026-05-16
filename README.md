# 냉장GOAT — 자영업자 발주 자동화 백엔드

> 사장님 인터뷰에서 발견된 한 가지 사실 — **"발주가 사장님 시간의 가장 큰 소비자"** — 를 중심으로 설계된 Spring Boot 3.5 / Java 21 백엔드.
> 캡스톤 팀 프로젝트, 박건우 백엔드 리드.

🇬🇧 영문 버전: [README.en.md](README.en.md) · 📜 v1 시점 보존본: [README_0315.md](README_0315.md)

---

## 🚀 30초 요약 (면접관용)

| 항목 | 내용 |
|---|---|
| 한 줄 | 자영업 식자재 **발주 의사결정 지원** 백엔드 (배달앱·POS·키오스크 3채널) |
| 박건우 역할 | 캡스톤 백엔드 리드 (sim 팀원 1명과 협업) |
| 핵심 기술 | Spring Boot 3.5 · Java 21 · MySQL 8 · Redis · Spring Batch · Flyway · Resilience4j |
| 검증된 자산 | 4종 락 전략 비교 (500-스레드 카오스 테스트) · 4-소스 가격 통합 (`GENERATED COLUMN STORED`) · KAMIS·EKAPE 6년 실측 buy-signal threshold |
| 진행 상황 | 동시성 축 검증 완료 / 가격 통합 모듈 빌드 통과 / **발주 시점 예측·알림(UC-CORE-3)은 미구현** |
| 의도적으로 안 한 것 | "30분 절약" 같은 운영 결과 표현 일체 X (UC-CORE-3 미구현이므로) |

---

## 🍳 어떤 문제를 풀고 있나? (도메인 설명, 비전공자도 OK)

식당 사장님은 매일 새벽 식자재 거래처 5곳에 일일이 문자·전화로 발주를 넣고, 가격은 손으로 비교하고, 재고는 종이에 적어 가며 발주량을 계산합니다. 송탄점·용인 막창점 두 매장 사장님을 직접 인터뷰한 결과 페인포인트가 한 가지로 수렴했습니다 — 사장님의 시간을 가장 많이 잡아먹는 일은 **발주**입니다.

| 페인포인트 | 매장 | 빈도 |
|---|---|---|
| 거래처 5곳에 개별 문자·전화 발주 | 송탄점 | 매주 |
| 재료 부족으로 판매 불가 | 송탄점 | 월 3~6건 |
| 발주 미스로 옆 매장에서 재료 빌림 | 용인 막창점 | 실제 발생 |
| 발주용 가격·시세 수동 비교 (원가율 40~50% 직접 계산) | 용인 막창점 | 상시 |
| 손글씨 재고 확인 → 발주량 계산 | 양쪽 공통 | **주당 3~5시간** |

→ 이 시스템의 목표는 **발주 의사결정 3축**을 코드로 지원하는 것입니다. 우선순위 *(뭐가 떨어질까)* · 가격 *(어디서 얼마에 사야 할까)* · 타이밍 *(언제 시킬까)*.

---

## 🔄 v0 → v2 컨셉 피봇 — "엔진은 람보르기니인데 운전석이 없다"

v0(2026-03 이전)은 동시성 락 전략 데모였습니다. 기술 자랑 중심으로 "재고 정합성을 코드로 보장한다"는 엔지니어 서사였습니다. 2026-04-05 plan.md에서 자기 비판이 들어갔습니다.

> *사장님은 데이터 정합성이 아니라 걱정과 수고를 덜어주는 대가로 월 3만원을 낸다.*

엔지니어가 자랑하고 싶은 락·MVCC·Redisson이 아니라 사용자가 돈 낼 이유가 무엇인지 다시 정의했습니다. 이후 모든 기능은 **발주 의사결정 지원**이라는 한 가지 목표에 정렬됐습니다.

---

## 🎯 발주 의사결정 3축 vs. 현재 코드 상태 (정직한 평가)

| 축 | 유스케이스 | 현재 상태 | 코드 진실 |
|---|---|---|---|
| 우선순위 | UC-CORE-1: 재고 임박 Top 5 | 🟡 부분 | InventoryBatch FIFO 쿼리는 있음. 전용 Top 5 API · 등급 색상 · 소진 예상일 계산은 아직 |
| **가격** | UC-CORE-2: 최저가 Top 5 | 🟡 v2 설계 완료 + 빌드 통과 | `pricing/` 모듈 17 파일, `compileJava` 통과. 통합 테스트 0건, V001 dev DB 미적용 |
| **타이밍** | **UC-CORE-3: 발주 시점 예측 + 알림** | ❌ **미구현** | `PurchaseOrder` 엔티티 · `/orders/forecast` · 알림 발송 모두 코드 0줄 |
| (이력) | **UC-SUP-8: 발주 이력 장부** | ❌ **미구현** | `PurchaseOrder` 엔티티 · `/purchase-orders` API 모두 없음 |

> **💡 정직한 한 줄.** 이 프로젝트의 헤드라인 약속(*"사장님 발주 시간 절감"*)은 UC-CORE-3 + UC-SUP-8 이 구현되어야 비로소 실현됩니다. 현재 빌드되어 있는 것은 그 위에 올릴 **기술 토대** — 정합성을 보장하는 주문 처리(Pillar 1)와 가격 비교 데이터 평면(Pillar 2) — 뿐입니다. "발주 시간 줄임" · "30분 절약" 같은 운영 결과 표현은 본 README 어디에도 없으며, 어떤 파생 포트폴리오에도 절대 등장하지 않습니다.

---

## 🛡 Pillar 1 — 동시성 제어 (v1, 검증 완료)

**상황.** 현대 식당은 배달앱 · 매장 POS · 키오스크 3채널에서 동시에 주문을 받습니다. 마지막 한 접시에 0.1초 간격으로 두 채널이 같은 행을 건드리면, 락 없는 시스템은 마지막 한 개를 두 번 팔고, 주방은 한 명에게 취소 전화를 걸고, 다음날 발주는 "팔린 적 없는 것을 팔린 것"으로 잘못 계산합니다. 발주 결정의 신뢰는 재고 숫자의 신뢰 위에서만 성립합니다.

### 락 전략 4종 + Circuit Breaker 폴백 구조

```
LockStrategy (인터페이스)
  ├─ NONE        — 락 없음 (실패 재현용으로 코드베이스에 의도적으로 보존)
  ├─ SPIN        — Redis SETNX 스핀 락
  ├─ REDISSON    — Redisson 분산 락 (프로덕션 기본)
  └─ PESSIMISTIC — MySQL SELECT FOR UPDATE

LockStrategyHolder ── AtomicReference<LockStrategy>  (런타임 교체 가능)

Resilience4j CircuitBreaker — REDISSON 감싸기
  ↓ 실패 횟수가 임계치 초과 시
  ↓
자동 폴백 → PESSIMISTIC (DB 단독으로 동일한 정합성 보장)
```

### 검증된 수치

**시나리오.** 초기 재고 100g, 500 스레드 × 1g 차감. 이론적 정답: 성공 100건 · 재고부족 실패 400건 · 잔여 0g.

#### 정합성 비교 (`ConcurrencyTest`, `LockType` 파라미터)

| 전략 | 성공 | 실패 | 잔여 재고 | 정합성 |
|---|:---:|:---:|:---:|:---:|
| NONE | 500 | 0 | **10g** | ❌ 깨짐 |
| SPIN | 100 | 400 | 0g | ✅ |
| REDISSON | 100 | 400 | 0g | ✅ |
| PESSIMISTIC | 100 | 400 | 0g | ✅ |

**왜 NONE 결과가 0g도 -400g도 아닌 10g인가?** 500개 스레드가 각자 `quantity=100`을 읽고 각자 `99`를 쓰는데, MySQL `REPEATABLE READ`의 Last-Writer-Wins가 그 중 하나만 남깁니다. **10g는 Lost Update의 실측 fingerprint이고, 이게 NONE 전략을 코드베이스에서 삭제하지 않은 이유**입니다. 회귀 검증의 ground truth.

#### 장애 모드 비교 (`ChaosTest` — `docker stop redis-test`)

| 전략 | Redis 장애 시 행동 | 결과 |
|---|---|:---:|
| SPIN | `RedisConnectionFailureException` 전파 | ❌ 장애 |
| REDISSON | 3회 실패 → CircuitBreaker `OPEN` → PESSIMISTIC 자동 폴백 | ✅ 무중단 |

라이브 trace (`ChaosTest`):
```
[REDISSON] Redis 정상: 주문 #1 성공
[REDISSON] docker stop redis-test
[REDISSON] 실패 1/3 → 2/3 → 3/3
[REDISSON] CircuitBreaker = OPEN
[REDISSON] 주문 시도 → PESSIMISTIC 폴백
[REDISSON] PESSIMISTIC 성공 ✅ — 서비스 무중단
```

CircuitBreaker 설정:
- **프로덕션**: `slidingWindowSize=10`, `failureRateThreshold=50%`, `waitDurationInOpenState=10s`, `permittedNumberOfCallsInHalfOpenState=3`
- **테스트 프로파일**: `slidingWindowSize=3`, `minimumNumberOfCalls=3`, `waitDurationInOpenState=5s`

### 의도적으로 제외한 수치

본 README는 **TPS · P95/P99 지연 · "수만 건 트랜잭션 내구성"을 주장하지 않습니다**. 측정 스크립트가 코드에 없어서요. 위 정합성 결과만이 측정 가능하고 테스트 스위트만으로 재현 가능한 자산입니다. 운영 수치는 어떤 파생 포트폴리오에도 등장하지 않습니다.

---

## 💰 Pillar 2 — 다중 소스 가격 통합 (v2, 설계 완료)

발주 결정 *"어디서 얼마에 사야 하나"* 는 이종 소스 간 단위 가격이 비교 가능해야 합니다. 시스템은 **4개 소스**를 통합합니다 (2026-05 EKAPE 추가):

```
                 ┌─────────────────────────────────────────────────┐
                 │  KAMIS  (공공 도매시장 데이터, XML)               │
                 │  EKAPE  (축산물 소비자가격 — 2026-05 신규)        │
                 │  네이버 쇼핑 API  (B2C 일반 쇼핑, on-demand)      │
                 │  식자재왕  (B2B 도매 — Selenium 크롤러)           │
                 └────────────────────┬────────────────────────────┘
                                      ▼
                 ┌─────────────────────────────────────────────────┐
                 │   price_records  (단일 canonical 테이블)        │
                 │   UNIQUE (source, raw_product_id,               │
                 │           DATE(fetched_at))   ← append-only     │
                 │                                                 │
                 │   unit_price_per_kg                             │
                 │     GENERATED ALWAYS AS                         │
                 │     (price * 1000 / weight_grams)               │
                 │     STORED                                      │
                 │   ← DB 엔진이 직접 계산 + 디스크 materialize     │
                 │     → INDEX 가능 + 정합성 contract              │
                 └─────────────────────────────────────────────────┘
                                      ▼
                 IngredientMatcher  @Scheduled(fixedDelay = 10분) + on-demand fallback
                 OnlinePriceAggregator  다중 소스 머지 → 최저 단위가 (동률 복수 허용)
                 BR2-11 fallback  전 소스 빈 응답 시 → 검색 URL CTA 노출
```

### B2C / B2B 미스매치 — 소스를 갈아치운 이유

원래 v0.3 계획은 **쿠팡 + 마켓컬리**였습니다. 둘 다 B2C 가격인데, 사장님은 사실 **B2B 도매 채널**에서 사고 있었습니다. 우리는 사장님이 절대 결제하지 않을 가격을 비교하고 있었던 셈입니다.

데이터 소스를 **네이버 쇼핑(B2C 일반) + 식자재왕(B2B 도매)**으로 갈아치운 결정은 도구 선택이 아니라 **원래 가정에 대한 자기 비판**이었습니다. (`plan_park_0423_*` 시리즈 기록 참조)

### `GENERATED ALWAYS AS ... STORED` — 팀원 갈등을 DB 계층에서 해결

진짜 의견 충돌이 있었습니다:

- **팀원 (sim)**: "파생 값(`unit_price_per_kg`)을 저장하면 정규화 위반 + 원본 `price`·`weight_grams`과 divergence 위험."
- **park (나)**: "그런데 `unit_price_per_kg`가 **인덱스 가능**해야 across-source 최저가 쿼리가 빠릅니다."

`GENERATED ALWAYS AS (...) STORED`로 양쪽 우려를 DB 계층에서 동시에 닫았습니다:

- DB 엔진이 derivation을 **보장**합니다. 애플리케이션 코드가 divergent value를 만들 수 없습니다
- 디스크에 materialize되므로 `INDEX (unit_price_per_kg)` + `ORDER BY unit_price_per_kg`가 빠릅니다
- 정합성 우려는 MySQL의 contract로 풀리지, 애플리케이션 규율로 풀리지 않습니다

### 도메인 정밀도: 액체 밀도 상수

부피 단위로 팔리는 식재료(간장·액젓·식용유·참기름·우유·꿀)는 "1mL = 1g"으로 가정하면 단위가가 ~20~40% 왜곡됩니다. 6개 상수표로 차단:

| 재료 | 밀도 (g/mL) |
|---|:---:|
| 간장 | 1.20 |
| 액젓 | 1.25 |
| 식용유 | 0.92 |
| 참기름 | 0.92 |
| 우유 | 1.03 |
| 꿀 | 1.40 |

5분 코드 작업이지만 시연 신뢰도가 통째로 달려있는 의사결정.

### KAMIS·EKAPE 카테고리별 buy-signal threshold — 6년 실측

가격 신호 임계값을 직감으로 정하지 않고, **KAMIS 6년 (2019~2024) 실측 데이터**의 카테고리별 변동성으로 도출했습니다 (2026-05-04 리캘리브레이션 commit `refactor(pricing): recalibrate KamisCategory thresholds with 6-year KAMIS data`).

| 카테고리 | Threshold | 의미 |
|---|:---:|---|
| 채소 | **0.17** | 변동성 높음, 17% 하락 시 buy-signal |
| 축산물 | **0.08** | 변동성 낮음, 8% 하락 시 buy-signal |
| 수산물 | **0.07** | 변동성 낮음, 7% 하락 시 buy-signal |
| 과일 | **0.13** | 중간, 13% 하락 시 buy-signal |
| 곡물 | **0.05** | 매우 안정, 5% 하락 시 buy-signal |
| 가공식품 | **0.03** | 가장 안정, 3% 하락 시 buy-signal |

사장님에게 *"지금 사세요"* 를 보여줄 때, **그 신호의 근거가 코드 어디에서 어떻게 나왔는지 추적 가능**해야 한다는 것이 출발점이었습니다.

---

## 🔔 2026-05 업데이트 (최신 작업)

5월 commits로 다음이 반영됐습니다:

| 변경 | 내용 |
|---|---|
| **EKAPE 통합** (`feat(ekape)`) | 축산물 소비자가격 일별 수집. KAMIS가 cover 못 하는 축산물 카테고리 보강 |
| **6년 데이터 리캘리브레이션** (`refactor(pricing)`) | KAMIS threshold를 6년(2019~2024) 실측 기반으로 재도출 |
| **알림 모듈 개선** (`refactor(alert)`) | Redis SETNX → 인메모리 `ConcurrentHashMap`으로 중복 방지 교체. 단일 인스턴스 환경에서 Redis 의존성 제거 |
| **재고 단건 입력 API** | 모바일에서 사장님이 한 재료만 빠르게 등록할 수 있는 단순 endpoint |
| **온보딩 API** | 신규 사용자가 매장 정보 · 재료 카탈로그를 첫 진입 시 입력하는 흐름 |
| **Docker / CI** | 로컬 개발 환경 표준화 + 기본 CI 단계 추가 |
| **배치 admin endpoint** | 운영자가 KAMIS 배치를 수동 트리거할 수 있는 관리자 API |
| **민감값 환경변수 외부화** | `application.properties` 비밀값을 환경변수로 추출 |
| **레시피 크롤러** (sim, 별도 브랜치) | 식자재왕 레시피 템플릿 크롤러 + 데이터 |

---

## 🛠 기술 스택

| 영역 | 기술 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 3.5.7, Spring Data JPA, Spring Batch, Spring Security *(코드 존재 / 일시 비활성화)* |
| DB | MySQL 8.0+ (`GENERATED COLUMN STORED`) |
| 마이그레이션 | Flyway (V001 → V003) |
| 캐시 · 분산 락 | Redis 7, Redisson 3.23.2 |
| 신뢰성 | Resilience4j 2.1.0 (CircuitBreaker) |
| 테스트 | JUnit 5, Testcontainers, CountDownLatch |
| 외부 API | KAMIS (XML), EKAPE, 네이버 쇼핑 API (JSON) |
| 외부 크롤러 (별도 브랜치, sim) | Python 3, Selenium, webdriver-manager (식자재왕 13개 카테고리) |

---

## 👤 박건우 역할 (캡스톤 백엔드 리드)

| 영역 | 박건우 담당 | sim 담당 |
|---|---|---|
| 도메인 룰 코드화 | KAMIS·EKAPE 6년 threshold 도출, 액체 밀도 상수, BR2-11 fallback CTA | — |
| 동시성 4종 전략 | LockStrategy 인터페이스 + 4구현체 + CircuitBreaker 폴백 | — |
| 가격 통합 모듈 | `pricing/` 17 파일 1차 구현, `compileJava` 통과, V001~V003 마이그레이션 | — |
| 의사결정 절충 | `unit_price_per_kg` 갈등 → `GENERATED COLUMN STORED` 합의 | — |
| 5월 작업 | EKAPE 통합, 6년 리캘리브레이션, 알림 인메모리 교체, 온보딩 API | — |
| 외부 크롤러 | — | 식자재왕 Python 크롤러, 레시피 템플릿 |

---

## 🗺 로드맵

### 단기 (UC-CORE-3 + UC-SUP-8 — 발주 자동화 핵심 약속 실현)

- [ ] `PurchaseOrder` 엔티티 + `OrderForecastScheduler` (소진 예상일 + 발주 요일 비교)
- [ ] `GET /orders/forecast` 알림 후보 endpoint
- [ ] `POST /purchase-orders` + `/purchase-orders/{id}/confirm` (발주 등록 · 완료)
- [ ] 알림 채널 (SMS / 앱 푸시)
- [ ] UC-CORE-1 재고 Top 5 endpoint + 등급 색상 + 소진 예상일 계산

### 중기 (Pillar 2 동작 검증)

- [ ] V001 마이그레이션 dev DB 적용 + 통합 테스트
- [ ] sim 크롤러 식자재왕 데이터 dev DB 적재 검증
- [ ] sim · park 두 브랜치 머지 전략 (도메인 충돌 정리)

### 장기

- [ ] Kubernetes 배포
- [ ] Prometheus / Grafana 운영 모니터링
- [ ] 발주 이력 누적 → V1 가격 추이 예측 고도화

---

## 📚 저장소 안 참고 문서

- `MD/plan_park_*` — 의사결정 흐름 (v0.3 → v0.4 피봇, sim 합의 회신 등)
- `crawler/ewangmart/` — sim 식자재왕 크롤러 (별도 브랜치)
- `src/main/resources/db/migration/V001~V003` — Flyway 마이그레이션
- [README.en.md](README.en.md) — 영문 버전
- [README_0315.md](README_0315.md) — v1 시점 보존본 (역사적 자료)

---

## 🔗 연락처

**박건우 ｜ Backend Engineer**

- 이메일: Gunwoo363@gmail.com
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)
