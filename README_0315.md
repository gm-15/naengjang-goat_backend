# 냉장GOAT v2.1

배달앱·키오스크·POS가 동시에 주문을 받는 환경에서 식자재 재고가
깨지지 않음을 설계하고 수치로 증명한 업소용 재고 관리 시스템.
Spring Boot 3 / Java 21 기반. 500 스레드 동시 요청 환경에서
4가지 락 전략을 비교하고, Redis 장애 시 무중단 자동 전환을 구현했다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Spring Boot 3, Java 21, JPA |
| DB | MySQL (REPEATABLE READ) |
| 동시성 | Redis, Redisson, Resilience4j |
| 테스트 | JUnit 5, CountDownLatch (500 스레드), Docker (카오스) |
| 인프라 | Docker Compose, AWS |

---

## 핵심 설계 결정 3가지

### 1. 재고 수량 — Double → BigDecimal 전환

**문제**
레시피 단위는 g, 재고 단위는 kg. 변환 연산이 반복되면
double의 부동소수점 오차가 누적된다.
수만 건의 트랜잭션 후 실재고와 DB 수치가 어긋나는 구조적 결함이 있다.

**결정**
모든 수량 필드를 BigDecimal로 전환하고,
UnitConverter에서 g/kg/ml/L 단위 정규화 후 HALF_UP 반올림 정책을 적용했다.

**이유**
double은 `0.1 + 0.2 = 0.30000000000000004`가 되는 언어 수준의 한계가 있다.
금융 시스템에서 BigDecimal을 쓰는 이유와 동일하다.
식자재 원가 계산에서 소수점 오차가 쌓이면 AvT 편차 분석 자체가 무의미해진다.

---

### 2. InventoryBatch — 총량 관리 → FIFO 배치 구조

**문제**
기존 Inventory 테이블은 stockQuantity 하나로 총량만 관리했다.
입고 날짜와 유통기한 정보가 없으니 어떤 재료를 먼저 써야 하는지 알 수 없고,
폐기 손실을 사전에 막을 방법도 없었다.

**결정**
Inventory 테이블을 제거하고 InventoryBatch로 대체했다.
입고 1건 = 배치 1행. `expiration_date ASC` 정렬로 유통기한 임박 배치를 먼저 소진한다.
총 재고량이 필요하면 `InventoryBatch SUM`으로 계산한다.

**이유**
FIFO는 식품 업계 표준 재고 관리 방식이다.
배치 단위로 쪼개야 유통기한 D-3 경고, 입고 단가 기반 원가 계산, AvT 편차 분석이
모두 가능해진다. 총량 하나로는 이 중 어느 것도 구현할 수 없다.

---

### 3. LockStrategy 패턴 + 트랜잭션 설계

**문제 1 — 락 전략**
기존 코드는 SaleService에 Redisson, InventoryService에 SpinLock이 이유 없이 혼재했다.
왜 그 전략을 선택했는지 근거가 없었고, 성능 비교 데이터도 없었다.

**결정 1**
LockStrategy 인터페이스 + 4개 구현체(NONE / SPIN / REDISSON / PESSIMISTIC)를 만들고
LockStrategyHolder(AtomicReference)로 런타임 전환이 가능하게 설계했다.
운영 기본값은 REDISSON, Redis 장애 시 Circuit Breaker가 PESSIMISTIC으로 자동 전환한다.

**문제 2 — 트랜잭션**
processOrder에 `@Transactional`을 붙이고 내부에서 REQUIRES_NEW를 사용하면
500 스레드 × 커넥션 2개 = HikariCP 풀 소진 → 커넥션 데드락이 발생했다.
(실제로 테스트에서 SPIN/REDISSON 정합성 실패로 확인)

**결정 2**
processOrder에서 `@Transactional`을 제거했다.
락 안에서 `StockDeductionService.deductFifo(@Transactional REQUIRED)`를 호출해
독립 트랜잭션이 즉시 커밋되도록 설계했다.
다음 스레드가 락을 획득하면 항상 최신 커밋 값을 읽는다.

---

## 동시성 테스트 결과

초기 재고 100g, 500 스레드 동시 요청, 1스레드당 1g 차감.
예상: 100건 성공 / 400건 재고 부족 실패 / 잔여 재고 0g.

### 정합성 비교 (ConcurrencyTest)

| 전략 | 성공 | 실패 | 잔여 재고 | 정합성 |
|------|:----:|:----:|:---------:|:------:|
| NONE | 500 | 0 | 10g | ❌ 붕괴 |
| SPIN | 100 | 400 | 0g | ✅ 보장 |
| REDISSON | 100 | 400 | 0g | ✅ 보장 |
| PESSIMISTIC | 100 | 400 | 0g | ✅ 보장 |

NONE 전략은 500건이 모두 통과해 재고 음수를 만들어야 하지만,
MySQL REPEATABLE READ의 Last-Writer-Wins로 인해 잔여 재고가 10g으로 남았다.
수치(10g ≠ 0g)가 Lost Update 발생을 증명한다.

### 장애 대응 비교 (ChaosTest — Redis 강제 종료)

| 전략 | Redis 장애 시 동작 | 결과 |
|------|-----------------|:----:|
| SPIN | `RedisConnectionFailureException` 전파 | ❌ 서비스 중단 |
| REDISSON | 3회 실패 → Circuit Breaker OPEN → PESSIMISTIC 자동 전환 | ✅ 무중단 |

```
[SPIN]     Redis 정상 상태: 주문 1건 성공
[SPIN]     Redis 강제 종료 완료
[SPIN]     Redis 장애 시 예외 발생 ✅: RedisConnectionFailureException
           → 결론: Fallback 없음. Redis 단일 장애점.

[REDISSON] Redis 정상 상태: 주문 1건 성공
[REDISSON] Redis 강제 종료 완료
[REDISSON] 실패 1/3 → 실패 2/3 → 실패 3/3
[REDISSON] Circuit Breaker 상태: OPEN
[REDISSON] Circuit OPEN 후 주문 시도 → PESSIMISTIC Fallback...
[REDISSON] PESSIMISTIC Fallback 성공 ✅ — 서비스 무중단 확인
```

---

## 운영 락 전략 흐름도

```
주문 요청
    │
    ▼
LockStrategyHolder.get() → REDISSON (기본값)
    │
    ▼
RedissonLockStrategy.executeWithLock()
    │
    ├─ [정상] ──────────────────────────────────────────▶ StockDeductionService.deductFifo()
    │                                                              │
    │                                                              ▼
    │                                                    FIFO 배치 소진 → 커밋
    │
    └─ [Redis 장애 / 실패율 50% 초과]
            │
            ▼
    Resilience4j Circuit Breaker OPEN
            │
            ▼
    fallbackToDb() 자동 호출
            │
            ▼
    PessimisticLockStrategy → SELECT FOR UPDATE
            │
            ▼
    동일 정합성 보장 / 서비스 무중단
            │
            ▼
    로그: [Circuit Open] Redis 장애 → DB Lock 전환
```

---

## 발표용 핵심 포인트 5줄

1. **락이 없으면 얼마나 깨지는지 직접 재현했다** — NONE 전략으로 500 스레드를 동시에 투입했을 때 100g 재고가 10g으로 남는 Lost Update를 수치로 확인했다.
2. **4가지 락 전략을 동일 조건에서 비교했다** — NONE / SPIN / REDISSON / PESSIMISTIC을 500 스레드 환경에서 측정해 정합성과 트랜잭션 설계의 트레이드오프를 데이터로 정리했다.
3. **Redis가 죽어도 서비스가 죽지 않는 구조를 만들었다** — Resilience4j 서킷 브레이커로 Redis 장애를 감지해 PESSIMISTIC 락으로 자동 전환하고, 주문 처리를 무중단으로 유지했다.
4. **커넥션 풀 데드락을 트랜잭션 설계로 해결했다** — processOrder의 @Transactional을 제거하고 StockDeductionService를 분리해, 500 스레드 환경에서 HikariCP 풀 소진 없이 정합성을 보장했다.
5. **재고 단위 연산의 정밀도 문제를 설계 수준에서 차단했다** — 모든 수량 필드를 BigDecimal로 전환하고 UnitConverter로 g/kg/ml/L 정규화 후 HALF_UP 정책을 적용해 부동소수점 오차 누적을 원천 차단했다.

---

## 면접 예상 질문 + 모범 답변

**Q1. NONE 전략에서 재고가 -400g이 아니라 10g이 남은 이유가 뭔가요?**

MySQL의 기본 격리 수준인 REPEATABLE READ에서 500 스레드가 동시에 quantity=100을 읽고 각자 -1씩 저장했습니다. 이때 Last-Writer-Wins 방식으로 마지막으로 커밋한 스레드의 값만 최종 반영됩니다. 결과적으로 실제 차감된 건수보다 훨씬 적은 수의 커밋만 유효하게 남아 10g이 된 것입니다. -400g이 아닌 10g이라는 수치 자체가 Lost Update가 발생했다는 증거입니다.

---

**Q2. processOrder에서 @Transactional을 제거한 이유가 무엇인가요?**

processOrder에 @Transactional이 있으면 락 안에서 StockDeductionService의 REQUIRES_NEW가 추가 커넥션을 요구합니다. 500 스레드가 동시에 실행되면 스레드당 커넥션 2개가 필요하고, HikariCP 풀이 소진돼 커넥션 데드락이 발생했습니다. 실제 테스트에서 SPIN과 REDISSON 전략 모두 정합성 실패로 확인한 뒤, processOrder의 @Transactional을 제거하고 StockDeductionService.deductFifo에 일반 @Transactional을 붙여 락 범위 안에서 즉시 커밋하도록 수정했습니다.

---

**Q3. FIFO를 어떻게 구현했나요? 동시성 문제는 없나요?**

InventoryBatch 테이블에서 `expiration_date ASC`로 정렬해 유통기한이 가장 임박한 배치부터 소진합니다. PESSIMISTIC 전략에서는 `findAllByIngredientIdWithPessimisticLock` 쿼리가 SELECT FOR UPDATE로 해당 배치 행에 DB 레벨 락을 걸어 동시 차감을 직렬화합니다. REDISSON 전략에서는 분산 락이 재고 차감 로직 전체를 감싸기 때문에 FIFO 정렬 결과도 락 범위 안에서 안전하게 소진됩니다.

---

**Q4. 서킷 브레이커 설정값을 어떻게 정했나요?**

`slidingWindowSize=10`, `failureRateThreshold=50`으로 설정했습니다. 최근 10번의 호출 중 50% 이상이 실패하면 서킷이 열립니다. `waitDurationInOpenState=10s` 동안 Redis로의 요청을 차단하고 PESSIMISTIC으로 전환합니다. 이후 `permittedNumberOfCallsInHalfOpenState=3`으로 Redis 복구 여부를 탐색합니다. 수치는 식당 피크타임 기준으로 Redis 순간 장애를 빠르게 감지하되, 일시적 지연으로 서킷이 불필요하게 열리지 않는 균형점으로 설정했습니다.

---

**Q5. BigDecimal 전환이 실제로 의미 있는 이유가 뭔가요? 어차피 소수점 몇 자리 차이 아닌가요?**

단건으로는 무시할 수 있는 수준이지만 문제는 누적입니다. double로 1g씩 수만 번 차감하면 오차가 쌓여 DB의 재고 수치와 실물 재고가 어긋납니다. 이 시스템에서 AvT 편차 분석은 이론적 소모량과 실제 소모량의 차이로 원가 손실을 측정하는데, 수량 자체에 오차가 있으면 편차 분석 결과를 신뢰할 수 없습니다. UnitConverter에서 g/kg 변환 시 ×1000 연산도 double이면 오차가 발생하므로 BigDecimal + HALF_UP으로 정책을 통일했습니다.

---

## 로컬 실행

```bash
# Redis 실행
docker run -d --name redis-test -p 6379:6379 redis:7-alpine

# 빌드 및 실행
./gradlew bootRun

# 동시성 테스트 (정합성 비교)
./gradlew cleanTest test --tests "*.ConcurrencyTest" --info

# 카오스 테스트 (Redis 장애 대응)
./gradlew test --tests "*.ChaosTest" --info
```

**API 호출 시 헤더 필수**
```
X-User-Id: {userId}
```
