# 냉장Goat v2 — 프로젝트 전략 마스터 문서

> 이 문서는 새로운 세션에서도 프로젝트 전략을 즉시 이어갈 수 있도록
> 배경, 결정 이유, 아키텍처, 우선순위까지 모든 컨텍스트를 담고 있다.
> 코드 구현 전 반드시 숙지할 것.

---

## 0. 프로젝트 정체성 (Identity)

### 이 프로젝트가 증명해야 하는 것

```
"기능 구현에서 멈추지 않고,
 운영 비용(FinOps), 데이터 정합성, 장애 격리(Resilience)를
 고려해 아키텍처를 설계하는 운영 중심의 백엔드 엔지니어"
```

### 포트폴리오 내 포지션

냉장Goat는 4개 프로젝트 포트폴리오 중 하나로,
**"극한의 동시성 제어와 데이터 정합성을 수치로 증명"** 하는 역할을 담당한다.

```
INSK_V4      → FinOps, LLM 토큰 비용 최적화
Clmakase     → 대용량 트래픽 인프라 (Kafka, EKS, 15만 VU)
ParkingMate  → P2P 도메인 설계, 비관적 락
냉장Goat v2  → 동시성 제어 전략 비교 + 장애 격리 + 데이터 정합성 실증
```

---

## 1. 프로젝트 배경

### 도메인

**소상공인 단일 매장을 위한 식자재 재고 관리 시스템**

- 실제 가게 1곳 섭외 → 실 운영 후 데이터 수집 예정
- 소상공인 도메인은 유지 (도메인 피벗 없음)
- 실 배포 = 최종 목표가 아닌 **엔지니어링 검증 수단**

### 왜 소상공인 도메인에 분산락이 필요한가 (핵심 방어 논리)

교수님/면접관이 "동네 식당에 분산락이 필요해?"라고 물을 때의 답:

```
현대 식당의 현실
  └── 배달의민족, 쿠팡이츠, 요기요 배달 앱 주문
  └── 매장 내 테이블 오더 (티오더 등)
  └── 카운터 POS 주문
  → 동시에 같은 재고를 차감하려는 다채널 경쟁 환경

The Last Material Problem
  재고: 스테이크 1인분 남음
  T+0.0s: 배달앱 주문 버튼 클릭
  T+0.1s: 테이블 오더 주문 확정
  → 락 없으면: 둘 다 처리 → 재고 마이너스 → 주방 취소 전화
  → 락 있으면: 먼저 들어온 주문이 재고 점유 → 다른 주문 즉시 품절 처리

결론: 전통 시스템은 '사람'이 정합성을 맞췄다.
      지능형 시스템은 '코드'가 정합성을 보장해야 한다.
```

---

## 2. v1의 문제점 (왜 v2로 전환했는가)

| 문제 | 내용 |
|------|------|
| 증명 없는 주장 | "Redis 분산락으로 동시성 해결" → 수치 없음 |
| 전략 없는 혼용 | SaleService: 스핀락, InventoryService: Redisson → 이유 없음 |
| AI 과장 | ML 발주 예측 ±15% → 2개월 데이터로 달성 불가능 |
| 단일 장애점 방치 | Redis 다운 시 전체 서비스 중단 → 대응 없음 |
| 도메인 피상적 모델링 | 재고 = 단순 수량 → 유통기한, 단위 불일치 미처리 |
| JWT/Security 노이즈 | 인증 코드 때문에 곁가지 면접 질문 유발 |

---

## 3. v2 핵심 전략 4가지

### 전략 1. 오버헤드 제거 (Pragmatic Engineering)

**제거 대상**

```
Spring Security 전체 설정
JWT 발급/검증/필터 로직
SaleItem 엔티티 (SaleHistory와 중복)
AI 발주 예측 (Scikit-learn 시계열 모델)
```

**대체 방안**

```
인증 → MockAuthFilter (X-User-Id 헤더 읽기)
       실 배포 직전 최소 인증으로 복구
AI   → 3단계 현실적 접근 (아래 섹션 참고)
```

---

### 전략 2. LockStrategy Pattern — 핵심 아키텍처

**목표**: "락을 구현했다" → "락 전략을 설계하고 성능을 비교했다"

```
LockStrategy (interface)
  │
  ├── NoLockStrategy          락 없음 → 데이터 붕괴 의도적 재현
  ├── SpinLockStrategy        Redis SETNX 반복 시도
  ├── RedissonLockStrategy    Pub/Sub 기반 + 서킷 브레이커 탑재
  └── PessimisticLockStrategy DB 비관적 락 → Redis 장애 시 Fallback

LockStrategyHolder
  └── AtomicReference<LockStrategy>
      → 런타임 전략 교체
      → PUT /admin/lock/switch?type=REDISSON

LockStrategyFactory
  └── LockType enum → Strategy 구현체 생성/반환
```

**패키지 구조**

```
global/lock/
  ├── LockType.java                    enum: NONE, SPIN, REDISSON, PESSIMISTIC
  ├── LockStrategy.java                interface
  ├── LockStrategyHolder.java          AtomicReference 기반 런타임 교체
  ├── LockStrategyFactory.java         구현체 생성
  └── impl/
      ├── NoLockStrategy.java
      ├── SpinLockStrategy.java
      ├── RedissonLockStrategy.java    @CircuitBreaker 포함
      └── PessimisticLockStrategy.java

inventory/controller/
  └── LockSwitchController.java        /admin/lock/* API
```

---

### 전략 3. 카오스 엔지니어링 — 서킷 브레이커

**시나리오**: Redis 서버 강제 다운 → 자동 Fallback

```
정상 흐름
  RedissonLockStrategy.executeWithLock()
  → Redis 분산락 획득 → 비즈니스 로직 → 락 해제

장애 흐름
  Redis 다운 → RedissonStrategy 5회 실패 감지
  → Resilience4j 서킷 OPEN
  → PessimisticLockStrategy 자동 Fallback
  → DB 비관적 락으로 동일 정합성 보장
  → 서비스 무중단 유지
  → 로그: "[Circuit Open] Redis 장애 → DB Lock 전환"
```

**Resilience4j 설정값 (면접 꼬리 질문 대비)**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      redisLock:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
```

---

### 전략 4. 도메인 깊이 — InventoryBatch + UnitConverter

#### InventoryBatch (유통기한 + FIFO)

```
기존 Inventory
  └── stockQuantity: 500.0 (깐마늘 500g)

v2 InventoryBatch 엔티티
  ├── 배치1: 200g, 입고 11/1, 유통기한 11/15 ← 먼저 소진 (FIFO)
  └── 배치2: 300g, 입고 11/5, 유통기한 11/20

기능
  → FIFO 소진: 유통기한 빠른 배치부터 차감
  → 유통기한 3일 이내 배치 경고 알림
  → 입고가(purchasePrice) 저장 → 원가 분석 가능
```

**면접 멘트**

> "식자재 도메인에서 재고 수량보다 중요한 것은 신선도라고 판단했습니다.
> 단일 수량 모델을 입고 배치 단위로 재설계하여 FIFO 소진과
> 유통기한 추적이 가능하도록 모델링했습니다."

#### UnitConverter (단위 정규화)

```
문제
  레시피: 파스타면 100g 필요
  재고:   파스타면 0.5kg 보유
  → 단위 불일치 → 차감 오류

해결
  모든 수량 연산을 기준 단위(g, ml, 개)로 정규화
  kg → g (*1000), L → ml (*1000)
  double 대신 BigDecimal + HALF_UP 반올림 정책
  → 부동소수점 누적 오차 제거
```

**면접 꼬리 질문 대비**

> Q: "소수점 오차는 어떤 자료형으로 해결했나요?"
> A: "double의 부동소수점 오차를 방지하기 위해 BigDecimal을 사용했고,
>     반올림 정책은 HALF_UP을 적용했습니다."

---

## 4. AI 발주 추천 — 3단계 현실적 접근

### 왜 ML 모델을 쓰지 않는가

```
시계열 ML 모델(ARIMA, Prophet)의 요구 데이터
  요일 패턴 학습: 최소 4~8주
  계절 패턴 학습: 최소 1년
  날씨 상관관계: 최소 3~6개월

우리 상황
  실 배포 ~ 학기 종료: 약 2~3개월
  → 계절 패턴 학습 불가
  → 데이터 부족 시 ML이 단순 평균보다 정확도 낮음
```

### 3단계 접근 (Cold Start → 고도화)

```
Phase 1. 배포 직후 (데이터 0~4주)
  규칙 기반 추천
  → 7일 가중 평균 (최근일수록 가중치 높게)
  → 요일 보정 (금/토는 평일 * 1.3 등 수동 설정)
  포인트: "데이터 없을 때 시스템이 어떻게 살아있는가"

Phase 2. 데이터 4~8주 누적
  외부 변수 연동
  → 날씨 API (기온, 강수량)
  → 간단한 선형 회귀 모델
    input:  요일, 기온, 강수 여부
    output: 예상 소비량
  포인트: "외부 변수를 어떻게 모델에 녹이는가"

Phase 3. KAMIS 가격 이력 연동 (콜드 스타트 우회)
  KAMIS 수년치 가격 데이터 보유 → 매장 데이터 없이 분석 가능
  → 가격 상승 시 해당 재료 포함 메뉴 소비량 변화 상관관계
  → 계절별 가격 패턴 → 발주 시점 추천
  포인트: "외부 데이터로 콜드 스타트를 우회"
```

**면접 멘트**

> "콜드 스타트 문제를 인식하고 단계적 접근을 채택했습니다.
> 초기에는 규칙 기반으로 시작했고, 데이터 누적 후 날씨 API와
> 선형 회귀로 전환했습니다. 현재 모델의 한계는 계절 패턴 학습을 위한
> 데이터 부족임을 인지하고 있으며, KAMIS 가격 이력으로 이를 보완했습니다."

---

## 5. 유지 / 제거 / 추가 목록

### 유지

```
소상공인 단일 매장 도메인
실제 가게 배포 계획
RawMaterial, Inventory, Recipe, RecipeItem, SaleHistory 엔티티
Redis 분산락 (전략 패턴으로 강화)
KAMIS 배치 (캐시 + 가격 분석으로 활용)
Spring Boot / MySQL / React / Docker / AWS 스택
```

### 제거

```
Spring Security + JWT 전체 구현 → MockAuthFilter 대체
SaleItem 엔티티 → SaleHistory와 중복
AI 시계열 예측 (Scikit-learn) → 3단계 현실적 접근으로 대체
```

### 추가

```
InventoryBatch 엔티티 (유통기한 + FIFO)
UnitConverter 레이어 (단위 정규화)
LockStrategy 인터페이스 + 4개 구현체
LockStrategyHolder (AtomicReference 런타임 교체)
Resilience4j 서킷 브레이커
LockSwitchController (/admin/lock/switch)
ConcurrencyTest (CountDownLatch 동시성 검증)
POS 주문 화면 (React)
재고 대시보드 + 유통기한 알림 (React)
판매 이력 화면 (React)
```

### 선택 구현 (Phase 3 이후, 핵심 수치 완성 후 진행)

```
AI 챗봇 인터페이스
  사장님이 자연어로 재고/유통기한/판매 현황을 질의
  LLM + RAG 기반 Hallucination 방지 구조
  토큰 비용 최적화 (INSK_V4 FinOps 경험 연결)
  → 상세 내용: 섹션 12 참고
```

---

## 6. 새 프레임 서사 (발표/보고서용)

### 프로젝트 한 줄 정의

> "식당 피크타임의 다중 기기 동시 주문에서 발생하는
> 데이터 정합성 붕괴를 설계-구현-수치로 증명한 재고 제어 시스템"

### 서론 (보고서/발표 첫 문단)

> 현대 식당은 배달 앱, 테이블 오더, 카운터 POS 등
> 다채널 주문이 동시에 같은 재고를 차감하려는 환경에서 운영된다.
> 재고가 딱 1인분 남은 상황에서 0.1초 간격으로 두 채널의 주문이 들어오면
> 락이 없는 시스템은 재고를 마이너스로 만든다.
>
> 본 프로젝트는 이 정합성 붕괴를 의도적으로 재현하고,
> 네 가지 락 전략(NONE / SpinLock / Redisson / Pessimistic)의
> TPS와 정합성 오차를 정량 비교했다.
> 추가로 Redis 단일 장애점 시나리오에서 서킷 브레이커를 통해
> DB 비관적 락으로 자동 전환되는 Fallback 구조를 설계하고,
> 실 매장 배포를 통해 운영 데이터를 수집했다.

### 기술 선택 근거 서사

> 먼저 락이 전혀 없는 상태(NONE)에서 500개 동시 요청을 발생시켰다.
> 결과는 재고 500에서 -463의 오차였다.
> 이 붕괴를 기준점으로 세 가지 락 전략을 동일 조건에서 비교했다.
>
> 스핀락은 구현이 단순하지만 Redis 장애 시 무한루프 위험이 있다.
> Redisson은 CPU 오버헤드가 낮지만 외부 의존성이 SPOF가 된다.
> 이 SPOF를 의도적으로 발생시키고, 서킷 브레이커로 DB 락에
> 자동 전환되는 Fallback 구조를 설계했다.

---

## 7. 예상 질문 & 답변 (면접/발표 대비)

### 교수님 압박 질문

```
Q: "지금 가게에서도 잘 돌아가지 않나? 이게 필요한가?"
A: "전통적 POS는 단일 기기 중심이라 동시성 문제가 없습니다.
    하지만 배달 앱 + 테이블 오더 + POS가 통합된 환경에서는
    0.1초 차이로 같은 재고를 두 채널이 동시에 차감하려 합니다.
    저는 이 환경을 시뮬레이션하고, 락이 없을 때의 붕괴를
    실제로 재현한 뒤 해결했습니다."

Q: "시중 POS도 이런 거 다 하지 않나?"
A: "시중 POS는 대부분 단일 기기 중심이거나 사람이 정합성을
    수동으로 맞추는 구조입니다. 저는 다채널 환경에서 0.1초
    레이턴시로 발생하는 정합성 오차를 코드로 제어하고,
    인프라 장애 시에도 서비스가 중단되지 않는 구조를 설계했습니다."

Q: "학생 수준에서 과한 설계 아닌가?"
A: "단순히 기능을 구현하는 코더를 넘어, 성능과 안정성을
    수치로 증명할 수 있는 엔지니어로 성장하기 위해
    의도적으로 이 난제를 설정했습니다.
    결과는 nGrinder 측정 수치로 보여드릴 수 있습니다."
```

### 기술 꼬리 질문

```
Q: "재고 마이너스를 어떻게 재현했나요?"
A: CountDownLatch 500 스레드 동시 실행,
   NONE 전략 적용 후 최종 재고 측정 → -463 오차 확인

Q: "Redis 장애 감지 후 Fallback까지 타임아웃이 몇 초인가요?"
A: slidingWindowSize=10, failureRateThreshold=50%,
   waitDurationInOpenState=10s로 설정

Q: "단위 변환 소수점 오차는 어떤 자료형으로 해결했나요?"
A: double 대신 BigDecimal, HALF_UP 반올림 정책 적용

Q: "7일 평균이 날씨 변수를 반영 못 하는 건 어떻게 해결했나요?"
A: 콜드 스타트 문제 인지 후 3단계 접근 채택.
   데이터 누적 후 날씨 API + 선형 회귀 전환 계획.
   KAMIS 가격 이력으로 콜드 스타트 우회.

Q: "스핀락 대신 Redisson을 선택한 이유는?"
A: 실측 결과 Redisson이 CPU 오버헤드 X% 낮았고,
   스핀락은 Redis 장애 시 무한루프 위험이 있기 때문.
   단, Redisson도 SPOF 위험이 있어 서킷 브레이커로 보완.
```

---

## 8. 동시성 테스트 목표 수치표

> 아래는 구현 후 채워야 할 목표 표다. X, Y, Z는 실측값으로 교체.

| 전략 | 동시 요청 | 정합성 오차 | 평균 지연 | Redis 장애 대응 |
|------|---------|-----------|---------|----------------|
| NONE | 500 | -463 (붕괴) | - | 서비스 중단 |
| SPIN | 500 | 0 | X ms | 서비스 중단 |
| REDISSON | 500 | 0 | Y ms | 자동 전환 ✅ |
| PESSIMISTIC | 500 | 0 | Z ms | 자동 전환 ✅ |

---

## 9. 우선순위 태스크

### Phase 1 — 기반 정리 (1~2주)

```
P1-1. 불필요 코드 제거
  삭제: SecurityConfig.java
  삭제: JwtAuthenticationFilter.java
  삭제: TokenProvider.java
  삭제: UserDetailsServiceImpl.java
  삭제: SaleItem.java + SaleItemRepository.java
  신규: global/filter/MockAuthFilter.java

P1-2. InventoryBatch 엔티티 설계
  신규: inventory/domain/InventoryBatch.java
  필드: rawMaterial, quantity, purchasedAt, expiresAt, purchasePrice
  관계: RawMaterial 1:N InventoryBatch

P1-3. UnitConverter 작성
  신규: global/util/UnitConverter.java
  기준단위: g, ml, 개
  BigDecimal + HALF_UP 적용
```

### Phase 2 — 핵심 아키텍처 (3~5주)

```
P2-1. LockStrategy 인터페이스 + 구현체 4개
  신규: global/lock/ 패키지 전체

P2-2. Resilience4j 서킷 브레이커
  대상: RedissonLockStrategy.java
  의존성: build.gradle에 resilience4j 추가

P2-3. LockSwitchController
  신규: inventory/controller/LockSwitchController.java
  API: PUT /admin/lock/switch?type={NONE|SPIN|REDISSON|PESSIMISTIC}
       GET /admin/lock/current

P2-4. InventoryRepository 쿼리 개선
  추가: findAllByUserId (Fetch Join)
  추가: findByRawMaterialIdWithLock (PESSIMISTIC_WRITE)

P2-5. InventoryService 리팩토링
  기존 Redisson 직접 호출 → LockStrategyHolder 위임으로 교체

P2-6. ConcurrencyTest 작성
  신규: test/.../ConcurrencyTest.java
  내용: @ParameterizedTest + @EnumSource(LockType.class)
        CountDownLatch 500 스레드 동시 실행
        전략별 정합성 오차 측정 + 출력
```

### Phase 3 — 화면 + 배포 (6~8주)

```
P3-1. POS 주문 화면 (React)
  메뉴 선택 → 수량 입력 → 주문 완료 → SaleService 호출

P3-2. 재고 대시보드 (React)
  잔량 목록 + 유통기한 임박 항목 경고 (빨간 하이라이트)
  재고 수동 입고 (InventoryBatch 추가)

P3-3. 판매 이력 화면 (React)
  오늘 판매 내역 + 메뉴별 판매량 차트

P3-4. Docker Compose + AWS 배포
  구성: Spring Boot + MySQL + Redis

P3-5. 실 매장 배포 → 운영 데이터 수집

P3-6. [선택] AI 챗봇 인터페이스
  전제조건: P3-1 ~ P3-5 완료 후 진행
  신규: chat/ 패키지 전체
  신규: 챗봇 UI 화면 (React)
  상세: 섹션 12 참고
```

### Phase 4 — 수치화 + 문서화 (9~10주)

```
P4-1. nGrinder 부하 테스트 시나리오 작성
P4-2. 전략별 TPS / Latency / 정합성 오차 측정표 완성
P4-3. 카오스 엔지니어링 시연 (Redis 강제 종료 영상)
P4-4. README 최종 정리
      흐름: 문제 정의 → 설계 → 수치 증명
P4-5. KAMIS 가격-소비량 상관관계 분석 결과 추가
```

---

## 10. 최종 체크리스트 (발표 전 완료 기준)

> 선택 구현(AI 챗봇)은 아래 체크리스트 완료 후 여유 있을 때 진행

```
□ 동작하는 POS 화면 데모
□ 전략별 동시성 비교표 (실측값으로 채워진 것)
□ Redis 강제 종료 → 자동 Fallback 시연 영상
□ 실 매장 배포 증거 (화면 캡처 or 운영 데이터)
□ 유통기한 임박 알림 동작 화면
□ CountDownLatch 테스트 코드 + 결과 출력
□ 꼬리 질문 10개 답변 준비
```

---

## 11. 절대 하지 말아야 할 것

```
❌ 면접에서 "배민 기술 블로그를 보고..." 로 시작하는 멘트
   → 블로그 따라 한 것처럼 보임
   → 대신: "설계 단계에서 이 문제를 인지하고 직접 측정했습니다"

❌ "WMS급 모델링"이라는 표현
   → WMS 관련 역질문 받으면 막힘
   → 대신: "입고 배치 단위 FIFO 소진 로직"

❌ 합성 데이터로 ML 모델 타당성 검토 주장
   → 실데이터와 분포 다름 → 의미 없다는 반박 받음
   → 대신: KAMIS 실제 가격 이력 데이터 분석

❌ 수치 없이 서사만 발표
   → 가장 치명적
   → 모든 주장에는 측정값이 있어야 함
```

---

---

## 12. [선택 구현] AI 챗봇 인터페이스

> **진행 조건**: Phase 3 핵심 태스크(P3-1 ~ P3-5) 완료 후 진행
> 핵심 수치(락 비교표, 서킷 브레이커)가 완성된 다음 추가

### 기능 개요

```
사장님이 자연어로 현황을 질의하면
시스템이 실시간 DB 데이터를 기반으로 답변

예시 대화
  사장님: "오늘 유통기한 곧 끝나는 재료 있어?"
  챗봇:   "네, 깐마늘 180g이 모레(11/15) 만료됩니다.
           파스타 메뉴에 우선 사용하시길 권장합니다."

  사장님: "이번 주 파스타 재료 충분해?"
  챗봇:   "이번 주 파스타 평균 소비량은 하루 300g인데,
           현재 파스타면 재고는 500g입니다.
           1.6일치 분량이 남아 있습니다.
           목요일 전에 발주를 권장합니다."
```

### 핵심 엔지니어링 문제: Hallucination 방지

```
❌ 나쁜 구현 (LLM이 추측해서 답변)
  사장님: "깐마늘 재고 얼마야?"
  챗봇:   "약 200g 정도 있을 것 같습니다" ← 추측값, 신뢰 불가

✅ 좋은 구현 (DB 데이터를 컨텍스트로 주입)
  1. DB에서 실시간 재고 조회
  2. LLM 시스템 프롬프트에 데이터 삽입
  3. LLM은 데이터를 자연어로 설명만 함

시스템 프롬프트 구조
  "당신은 식당 재고 관리 어시스턴트입니다.
   아래 데이터만을 기반으로 답변하세요.
   데이터에 없는 내용은 '확인이 필요합니다'라고 답하세요.

   [현재 재고 현황 - 2024/11/12 14:30 기준]
   - 깐마늘: 배치1 180g (만료 11/15), 배치2 300g (만료 11/20)
   - 파스타면: 500g (만료 11/25)
   - 토마토소스: 2캔 (만료 12/01)

   [오늘 판매 현황]
   - 토마토 파스타 5개 (파스타면 500g, 토마토소스 250g 소진)
   - 스테이크 2개

   [유통기한 임박 (3일 이내)]
   - 깐마늘 배치1: 180g, 11/15 만료 (D-3)"
```

### 구현 아키텍처

```
[React 챗봇 UI]
    ↓ POST /chat/message { userId, message }
[ChatController]
    ↓
[ChatService]
    ├── InventoryContextBuilder.buildContext(userId)
    │     ├── inventoryRepository.findAllByUserId()
    │     ├── inventoryBatchRepository.findExpiringWithin(3days)
    │     └── saleHistoryRepository.findTodaySummary()
    │     → 구조화된 텍스트 컨텍스트 생성
    │
    └── LlmApiClient.chat(systemPrompt + context, userMessage)
          → LLM API 호출
          → 자연어 답변 반환
    ↓
[ChatResponseDto 반환]
```

### 패키지 구조

```
chat/
  ├── controller/
  │   └── ChatController.java             POST /chat/message
  ├── service/
  │   ├── ChatService.java                흐름 오케스트레이션
  │   └── InventoryContextBuilder.java    DB 조회 → 프롬프트 텍스트 변환
  ├── client/
  │   └── LlmApiClient.java               LLM API 호출
  └── dto/
      ├── ChatMessageDto.java             { userId, message }
      └── ChatResponseDto.java            { answer, contextSummary }
```

### 답변 가능 범위

```
✅ 재고 현황
  "XX 재료 얼마나 남았어?"
  "오늘 부족한 재료 있어?"
  "이번 주 재료 충분해?"

✅ 유통기한 관리
  "곧 버려야 할 재료 있어?"
  "유통기한 임박 재료로 만들 수 있는 메뉴 뭐야?"

✅ 판매 현황
  "오늘 제일 많이 팔린 메뉴 뭐야?"
  "이번 주 파스타 재료 얼마나 썼어?"

✅ 발주 추천
  "내일 뭐 주문해야 해?"
  "이번 주 소비 패턴으로 다음 주 발주량 알려줘"

❌ 답변 불가 (정직하게 모른다고 처리)
  "다음 달 매출 예측해줘" → 데이터 부족
  "경쟁 가게 메뉴 어때?" → 외부 데이터 없음
```

### FinOps 연결 (포트폴리오 각도)

```
문제
  매 질문마다 전체 재고 데이터를 컨텍스트로 전송
  → 재고 항목 100개 * 평균 50토큰 = 5,000 토큰/질문
  → 월 1,000회 질문 시 비용 급증

최적화 전략
  Step 1. 질문 분류 (재고 관련 / 판매 관련 / 발주 관련)
  Step 2. 분류에 따른 선택적 컨텍스트 구성
          재고 질문 → 재고 데이터만
          판매 질문 → 판매 데이터만
          발주 질문 → 재고 + 판매 + 소비 패턴
  Step 3. 재고 스냅샷 5분 캐싱 (동일 컨텍스트 재사용)

예상 효과: 토큰 사용량 40~60% 절감
```

**면접 멘트**

> "LLM API 호출 시 컨텍스트 크기가 비용에 직결되기 때문에,
> 질문을 먼저 분류하여 필요한 데이터만 선택적으로 주입했습니다.
> INSK_V4 프로젝트에서 경험한 LLM 토큰 비용 최적화 원칙을
> 그대로 적용했습니다."

### 챗봇 추가 시 포트폴리오 서사 변화

```
기존 서사
  "동시성 제어와 장애 대응을 수치로 증명한 재고 시스템"

챗봇 추가 후
  "엔지니어링 견고성(동시성/장애)을 기반으로,
   사장님이 자연어로 데이터에 접근할 수 있는
   완결된 지능형 재고 시스템"

추가되는 기술 레이어
  백엔드 엔지니어링 (기존) + LLM + RAG + FinOps
```

---

> **핵심 메시지**
>
> 서사는 확정됐다. 아키텍처도 확정됐다.
> 남은 것은 단 하나 — 실제로 구현하고 수치를 채우는 것이다.
> "그래서 제가 직접 측정한 결과입니다"라며 보여주는 표 하나가
> 모든 설명보다 강하다.
